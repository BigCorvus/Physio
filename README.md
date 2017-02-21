# Physio
![alt tag](https://github.com/BigCorvus/Physio/blob/master/Pics/Physio1.0.jpg)

My take on an (almost) open source development platform for a versatile wireless monitoring device that combines ECG, impedance pneumography, photoplethysmography, SPO2 calculation, galvanic skin response, NIBP wrist measurement as well as well as body temperature measurement in one wearable. 

Physio is based on a CK101 wrist blood pressure monitor, a CMS50C pulse oximeter, an ADS1292R analog front end for ECG and impedance pneumography (respiration monitoring via ECG electrodes), a MAX30205 human body temperature sensor and an ADXL345 accelerometer. The GSR measurement is also working fine so far, but it's interfering with the ADS1292R so only one of them can be used at a time. A Teensy 3.2 ties everythings together and uses an HC-05 bluetooth module for communication.
The carrier PCB of the current Physio version is designed for the great ADS1292R breakout board by protocentral->https://github.com/Protocentral/ADS1292rShield_Breakout

The Android app is based on the Amarino platform, so you have to download and install the Amarino App in order for this app to work (https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/amarino/Amarino_2_v0_55.apk). The recording feature is not working yet and will be improved in the future.

In order to get the best ECG and respiration data I recommend placing the electrodes according to the "Einthoven II" lead.

Note: this is not a medical device, but a development platform for educational and research purposes. I'm assuming you know what you're doing.
