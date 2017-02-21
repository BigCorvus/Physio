/*

Arduino Library for Maxim Integrated MAX30205 Human Body Temperature Sensor
Written by AA for ClosedCube
---

The MIT License (MIT)

Copyright (c) 2017 ClosedCube Limited

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

*/

#ifndef _CLOSEDCUBE_MAX30205_h

#define _CLOSEDCUBE_MAX30205_h
#include <Arduino.h>

typedef enum {
	TEMPERATURE = 0x00,
	CONFIGURATION = 0x01,
	T_HYST = 0x02,
	T_OS = 0x03,
} MAX30205_Registers;

class ClosedCube_MAX30205 {
public:
	ClosedCube_MAX30205();

	void begin(uint8_t address);

	float readTemperature();
	float readT(); // short-cut for readTemperature

private:
	uint8_t _address;
	uint16_t readData(uint8_t pointer);
};

#endif

