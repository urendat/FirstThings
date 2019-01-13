package com.urendat.firstthings;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity {
  private static final String TAG = MainActivity.class.getSimpleName();
  protected Gpio mRedLedGpio;
  protected Gpio mGreenLedGpio;

  // MQTT client
  MqttAndroidClient client;
  IMqttToken token;
  MqttConnectOptions options;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    PeripheralManager service = PeripheralManager.getInstance();

    // Run this method to print the RPi GPIO list to the Logcat console
    Log.d(TAG, "Available GPIO: " + service.getGpioList());

    // MQTT
    connectMqtt();

    try {
      Log.d(TAG, "Open GPIO: " + BoardDefaults.getGPIOForRedLED() + BoardDefaults.getGPIOForGreenLED());
      mRedLedGpio = service.openGpio(BoardDefaults.getGPIOForRedLED());
      mRedLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
      mGreenLedGpio = service.openGpio(BoardDefaults.getGPIOForGreenLED());
      mGreenLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
    } catch (IOException e) {
      Log.e(TAG, "Error on PeripheralIO API", e);
    }

    brokerStatus = findViewById(R.id.broker);

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

  private void connectMqtt()
  {
//    String clientId = MqttClient.generateClientId();
    String clientId = "PiThings";
    String brokerUri = "tcp://192.168.1.66:1883";
    client = new MqttAndroidClient(this.getApplicationContext(), brokerUri, clientId);
    client.setCallback(new MqttCallback() {
      @Override
      public void connectionLost(Throwable throwable) {
        updateBrokerStatus("MQTT connection lost");
        Log.e(TAG, "MQTT connection lost", throwable);
      }

      @Override
      public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
        updateBrokerStatus("MQTT connection OK");
        Log.d(TAG, mqttMessage.toString());
      }

      @Override
      public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

      }
    });

    try {
      options = new MqttConnectOptions();
      options.setUserName("urendat");
      options.setPassword("Smegtoz1".toCharArray());
      token = client.connect(options);
      token.setActionCallback(new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
          // We are connected
          Log.d(TAG, "MQTT broker connection success");
          subscribeToTopic();
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
          // Something went wrong e.g. connection timeout or firewall problems
          Log.d(TAG, "MQTT broker connection failed");
        }
      });
    } catch (MqttException e) {
      Log.e(TAG, "Error on MQTT initialization", e);
    }

  }

  // 15:47:26 MQT: tele/sonoff/STATE = {"Time":"2019-01-12T15:47:26","Uptime":"0T00:03:23","Vcc":3.486,"POWER":"OFF",
  // "Wifi":{"AP":1,"SSId":"TANNET2","BSSId":"C4:3D:C7:70:28:00","Channel":1,"RSSI":74}}
  private void subscribeToTopic() {
    try {
      client.subscribe("tele/sonoff/STATE", 0, null, new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
          Log.d(TAG,"Subscribed!");
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
          Log.d(TAG, "Subscribed fail!");
        }
      });

    } catch (MqttException e) {
      Log.e(TAG,"Exception subscribing", e);
    }
  }

  private void updateBrokerStatus(String status)
  {
    brokerStatus.setText(status + " : " + DateFormat.getDateTimeInstance().format(new Date())); // No localization is possible here
  }

  private void handleNewState(Gpio ledGpio, Boolean desiredStatus) {
    Log.d(TAG, "Set value for = " + ledGpio.getName() + " to " + desiredStatus.toString());
    setLed(ledGpio, desiredStatus.booleanValue());

    // Check connection
    if (!client.isConnected()) {
      try {
        client.connect(options);
      } catch (MqttException e) {
        Log.e(TAG, "Error on MQTT connection", e);
        return;
      }
    }

    String topic = "cmnd/sonoff/power";
    String payload = desiredStatus.booleanValue() ? "on" : "off";
    byte[] encodedPayload = new byte[0];
    try {
      encodedPayload = payload.getBytes("UTF-8");
      MqttMessage message = new MqttMessage(encodedPayload);
      // Retained message is cached on broker and last good message is sent to newly connected clients
      // message.setRetained(true);
      client.publish(topic, message);
    } catch (UnsupportedEncodingException | MqttException e) {
      Log.e(TAG, "Exception on MQTT send message", e);
    }

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

  // UUID
//  private static final String UUID_KEY = "_UUID";
//  private static final String PREFS_NAME = "MyPrefs";

  private String getDeviceId() {
//    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
//    if(!prefs.contains(UUID_KEY)) {
//      prefs.edit().putString(UUID_KEY, UUID.randomUUID().toString()).apply();
//    }
//    return prefs.getString(UUID_KEY, UUID.randomUUID().toString());

    return "af1aac79-19e1-461e-8b99-7c4861fa31f1";
  }

//  class ClientCallback extends MqttCallback
//  {
//
//  }

  // Firebase database interface
  private boolean mRedStatus = false;
  private DatabaseReference mCurrentRedStatusRef;
  private DatabaseReference mDesiredRedStatusRef;

  private boolean mGreenStatus = false;
  private DatabaseReference mCurrentGreenStatusRef;
  private DatabaseReference mDesiredGreenStatusRef;

  TextView brokerStatus;
}
