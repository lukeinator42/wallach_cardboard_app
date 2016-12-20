# Wallach Illusion Google Cardboard App

This repository contains a Google Cardboard App for testing the Wallach Auditory Illusion. 

## Where Can I Get this App?

The first and easiest option is to download the app from the Google Play Store, [here](https://play.google.com/store/apps/details?id=com.tatalab.wallachillusion). 

The other option is to clone this repo, and run the project from Android Studio. 

## How to Use the App

The user wears a pair of headphones that are plugged into an android phone running the app. The phone is then placed in a VR Headset and worn. The user is shown 3 different cubes:

- A red cube that remains stationary
- A blue cube that moves with the users head
- A green cube that moves twice as fast as the users head

![Screenshot](images/two-cubes.png)

Each trial lasts 20 seconds. The user is then presented with the three cubes in a random order, and selects which cube the sound appeared to becoming from. Selections are made by looking at the cube to be selected for 1.5 seconds. The cube is highlighted in yellow when the user is looking at it, as shown below: 

![Screenshot](images/menu-selection.png)


## How to Collect Data From the App

To collect data from the app wirelessly, first make sure you have the android developer tools installed. Then, make sure USB debugging is enabled on the phone, and connect it to the computer with a USB cable. In a terminal, type 

```
adb devices
```
and make sure the device is shown. Then type

```
adb tcpip 5555
```
to enable wireless debugging on the device. Now you can disconnect the usb cable. Then, to log data from the device, use adb logcat. For example, you can log the users head movements to a file by running

```
adb logcat System.out:I *:S | grep head-position > head-position.txt
```

or the menu selections the user makes

```
adb logcat System.out:I *:S | grep menu-selection > menu-selection.txt
```