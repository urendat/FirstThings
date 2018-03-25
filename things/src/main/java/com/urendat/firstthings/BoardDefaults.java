package com.urendat.firstthings;

import android.os.Build;

/**
 * Created by urendat on 2/26/18.
 */

@SuppressWarnings("WeakerAccess")
public class BoardDefaults {
  private static final String DEVICE_RPI3 = "rpi3";
  private static final String DEVICE_IMX6UL_PICO = "imx6ul_pico";
  private static final String DEVICE_IMX7D_PICO = "imx7d_pico";

  /**
   * Return the GPIO pin that the LED is connected on.
   * For example, on Intel Edison Arduino breakout, pin "IO13" is connected to an onboard LED
   * that turns on when the GPIO pin is HIGH, and off when low.
   */
  public static String getGPIOForRedLED() {
    switch (Build.DEVICE) {
      case DEVICE_RPI3:
        return "BCM6";
      case DEVICE_IMX6UL_PICO:
        return "GPIO4_IO22";
      case DEVICE_IMX7D_PICO:
        return "GPIO2_IO02";
      default:
        throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);
    }
  }
  public static String getGPIOForGreenLED() {
    switch (Build.DEVICE) {
      case DEVICE_RPI3:
        return "BCM5";
      case DEVICE_IMX6UL_PICO:
        return "GPIO4_IO22";
      case DEVICE_IMX7D_PICO:
        return "GPIO2_IO02";
      default:
        throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);
    }
  }
}
