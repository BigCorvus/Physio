/**************************************************************************************

This is example for ClosedCube MAX30205 Human Body Temperature Sensor Breakout

Initial Date: 11-Jan-2017

Hardware connections for Arduino Uno:
	VDD to 3.3V DC
	SCL to A5
	SDA to A4
	GND to common ground

Written by AA for ClosedCube

MIT License

**************************************************************************************/

#include <Wire.h>
#include "ClosedCube_MAX30205.h"

ClosedCube_MAX30205 max30205;

void setup()
{
	Serial.begin(9600);
	Serial.println("ClosedCube MAX30205 Arduino Demo");

	max30205.begin(0x48);
}

void loop()
{
	Serial.print("T=");
	Serial.print(max30205.readTemperature());
	Serial.println("C");
	delay(300);
}

