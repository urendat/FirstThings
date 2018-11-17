package com.urendat.firstthings;

import android.app.Activity;
import android.media.MediaPlayer;
import android.media.TimedMetaData;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;

public class MainActivity extends Activity {
  private static final String TAG = MainActivity.class.getSimpleName();
  private static final int INTERVAL_BETWEEN_BLINKS_MS = 2000;
  protected Gpio mRedLedGpio;
  protected Gpio mGreenLedGpio;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    PeripheralManager service = PeripheralManager.getInstance();

    // Run this method to print the RPi GPIO list to the Logcat console
    Log.d(TAG, "Available GPIO: " + service.getGpioList());

    mPlayer = MediaPlayer.create(this.getBaseContext(), R.raw.thunderstruck);
    mPlayer.setVolume(0.75f, 0.75f);
    mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
      @Override
      public boolean onError(MediaPlayer mp, int what, int extra)
      {
        Log.d(TAG, "Media Player error, position = " + mp.getCurrentPosition());
        return false;
      }
    });

//    LinearLayout mainView = (LinearLayout) findViewById(R.id.activity_main);
//    LayoutInflater inflater = getLayoutInflater();
//    View childView = inflater.inflate(R.layout.activity_main, mainView, false);
//    ToggleButton playButton = childView.findViewById(R.id.playButton);
//    playButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//        if (isChecked) {
//          // The toggle is enabled
//          Log.d(TAG, "PLAY ON");
//
//        } else {
//          // The toggle is disabled
//          Log.d(TAG, "PLAY OFF");
//          if (mPlayer.isPlaying())
//            mPlayer.pause();
//        }
//      }
//    });

    final LightRunner lightRunner = new LightRunner();
    final Thread t = new Thread(lightRunner);

    final TextView playMessage = findViewById(R.id.playMessage);
    final Switch playSwitch = findViewById(R.id.playSwitch);
    playSwitch.setOnCheckedChangeListener(
      new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          if (playSwitch.isChecked()) {
            playMessage.setText("Play On");
            Log.d(TAG, "Play switch: " + playSwitch.getTextOn());

            // Start lights if this is not starting from Paused state
            if (t.getState() == Thread.State.NEW) {
              t.start();
            }

            lightRunner.stopRunning = false;
            mPlayer.start();
          }
          else {
            playMessage.setText("Play Off");
            Log.d(TAG, "Play switch: " + playSwitch.getTextOff());
            if (mPlayer.isPlaying()) {
              mPlayer.pause();
            }
            else {
              mPlayer.stop();
            }
            lightRunner.stopRunning = true;
          }
        }
      });

    SeekBar skBar = findViewById(R.id.seekBar);
    skBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        frequency = progress;
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
      }
    });

    // TODO: Run the MediaPlayer as a Service, no on the UI thread.
    //  https://developer.android.com/guide/topics/media/mediaplayer.html
    // TODO: Use MediaPlayer Listeners to track state changes and monitor them here?

    mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override
      public void onCompletion(MediaPlayer mp) {
        Log.d(TAG, "Play complete");
        mp.reset();
        t.interrupt();
      }
    });
    //  setOnInfoListener()
    mPlayer.setOnTimedMetaDataAvailableListener(new MediaPlayer.OnTimedMetaDataAvailableListener() {
      @Override
      public void onTimedMetaDataAvailable(MediaPlayer mp, TimedMetaData data) {
        Log.d(TAG, "Timed Metadata: " + data.getTimestamp());
      }
    });

    //  https://github.com/googlesamples/android-SimpleMediaPlayer/

    try {
      Log.d(TAG, "Open GPIO: " + BoardDefaults.getGPIOForRedLED() + BoardDefaults.getGPIOForGreenLED());
      mRedLedGpio = service.openGpio(BoardDefaults.getGPIOForRedLED());
      mRedLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
      mGreenLedGpio = service.openGpio(BoardDefaults.getGPIOForGreenLED());
      mGreenLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
    } catch (IOException e) {
      Log.e(TAG, "Error on PeripheralIO API", e);
    }

    FirebaseDatabase database = FirebaseDatabase.getInstance();

    String deviceId = getDeviceId();
    Log.d(TAG, "Device ID = " + deviceId);

    mCurrentRedStatusRef = database.getReference(deviceId).child("currentRedStatus");
    mRedStatus = getLed(mRedLedGpio);
    mCurrentRedStatusRef.setValue(mRedStatus);

    mCurrentGreenStatusRef = database.getReference(deviceId).child("currentGreenStatus");
    mGreenStatus = getLed(mGreenLedGpio);
    mCurrentGreenStatusRef.setValue(mGreenStatus);

    mDesiredRedStatusRef = database.getReference(getDeviceId()).child("desiredRedStatus");
    mDesiredRedStatusRef.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        if(dataSnapshot.getValue() == null) {
          return;
        }
        mDesiredRedStatusRef.removeValue();
        Object desiredValue = dataSnapshot.getValue();
        handleNewState(mRedLedGpio, (Boolean)desiredValue); // IF we had a Status class, we would pass in Status.class
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
        Log.e(TAG, "Error on Firebase read", databaseError.toException());
      }
    });

    mDesiredGreenStatusRef = database.getReference(getDeviceId()).child("desiredGreenStatus");
    mDesiredGreenStatusRef.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        if(dataSnapshot.getValue() == null) {
          return;
        }
        mDesiredGreenStatusRef.removeValue();
        Object desiredValue = dataSnapshot.getValue();
        handleNewState(mGreenLedGpio, (Boolean)desiredValue); // IF we had a Status class, we would pass in Status.class
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
        Log.e(TAG, "Error on Firebase read", databaseError.toException());
      }
    });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    if (mPlayer != null)
      mPlayer.release();

    try {
      if (mRedLedGpio != null) {
        mRedLedGpio.close();
      }
      if (mGreenLedGpio != null) {
        mGreenLedGpio.close();
      }
    } catch (IOException e) {
      Log.e(TAG, "Error on PeripheralIO API", e);
    }
  }

  private void handleNewState(Gpio ledGpio, Boolean desiredStatus) {
    Log.d(TAG, "Set value for = " + ledGpio.getName() + " to " + desiredStatus.toString());
    setLed(ledGpio, desiredStatus.booleanValue());
  }


  protected void setLed(Gpio ledGpio, boolean newState) {
    try {
      ledGpio.setValue(newState);
    } catch (IOException e) {
      Log.e(TAG, "Error on PeripheralIO API", e);
    }

    if (ledGpio == mRedLedGpio)
    {
      mRedStatus = newState;
    }
    else {
      mGreenStatus = newState;
    }

    getDatabaseRef(ledGpio).setValue(newState);

  }

  private boolean getStatus(Gpio ledGpio)
  {
    if (ledGpio == mRedLedGpio)
    {
      return mRedStatus;
    }
    else {
      return mGreenStatus;
    }
  }

  private DatabaseReference getDatabaseRef(Gpio ledGpio)
  {
    if (ledGpio == mRedLedGpio)
    {
      return mCurrentRedStatusRef;
    }
    else return mCurrentGreenStatusRef;

  }

  private boolean getLed(Gpio ledGpio) {
    try {
      return ledGpio.getValue();
    } catch (IOException e) {
      Log.e(TAG, "Error on PeripheralIO API", e);
    }
    return false;
  }

  private class LightRunner implements Runnable {
    public boolean stopRunning = false;

    @Override
    public void run() {
      try {
        while (!stopRunning) {
          Thread.sleep(100 - frequency);
          Log.d(TAG, "Turn on light");
          setLed(mGreenLedGpio, true);
          Thread.sleep(50);
          Log.d(TAG, "Turn off light");
          setLed(mGreenLedGpio, false);
        }
      } catch (Throwable t) {
      }
    }
  }

  // UUID
  private static final String UUID_KEY = "_UUID";
  private static final String PREFS_NAME = "MyPrefs";

  private String getDeviceId() {
//    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
//    if(!prefs.contains(UUID_KEY)) {
//      prefs.edit().putString(UUID_KEY, UUID.randomUUID().toString()).apply();
//    }
//    return prefs.getString(UUID_KEY, UUID.randomUUID().toString());

    return "af1aac79-19e1-461e-8b99-7c4861fa31f1";
  }

  // Firebase database interface
  private boolean mRedStatus = false;
  private DatabaseReference mCurrentRedStatusRef;
  private DatabaseReference mDesiredRedStatusRef;

  private boolean mGreenStatus = false;
  private DatabaseReference mCurrentGreenStatusRef;
  private DatabaseReference mDesiredGreenStatusRef;

  private MediaPlayer mPlayer;
  int frequency = 50;
}
