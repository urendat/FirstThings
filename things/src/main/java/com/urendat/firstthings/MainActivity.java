package com.urendat.firstthings;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;

public class MainActivity extends Activity {
  private static final String TAG = MainActivity.class.getSimpleName();
  private static final int INTERVAL_BETWEEN_BLINKS_MS = 1000;
  private Gpio mRedLedGpio;
  private Gpio mGreenLedGpio;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    PeripheralManagerService service = new PeripheralManagerService();

    // Run this method to print the RPi GPIO list to the Logcat console
    // Log.d(TAG, "Available GPIO: " + service.getGpioList());

    try {
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


  private void setLed(Gpio ledGpio, boolean newState) {
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

}
