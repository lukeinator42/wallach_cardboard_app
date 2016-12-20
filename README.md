# Wallach Illusion Google Cardboard App

This repository contains a Google Cardboard App for testing the Wallach Auditory Illusion. 

## How to Use the App

The user wears a pair of headphones that are plugged into an android phone running the app. The phone is then placed in a VR Headset and worn. The user is shown 3 different cubes:

- A red cube that remains stationary
- A blue cube that moves with the users head
- A green cube that moves twice as fast as the users head

![Screenshot](images/two-cubes.png)

Each trial lasts 20 seconds. The user is then presented with the three cubes in a random order, and selects which cube the sound appeared to becoming from. Selections are made by looking at the cube to be selected for 1.5 seconds. The cube is highlighted in yellow when the user is looking at it, as shown below: 

![Screenshot](images/menu-selection.png)


adb logcat System.out:I *:S | grep head-position > head-position.txt

adb logcat System.out:I *:S | grep menu-selection > menu-selection.txt
