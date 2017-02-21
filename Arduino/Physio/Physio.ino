/*
  Patient Monitor with Blood pressure monitor CK-101 and Pulse oximeter CONTEC CMS 50C
  On Teensy 3.1
*/
// Arduino Wire library is required if I2Cdev I2CDEV_ARDUINO_WIRE implementation
// is used in I2Cdev.h
#include "Wire.h"
// I2Cdev and ADXL345 must be installed as libraries, or else the .cpp/.h files
// for both classes must be in the include path of your project
#include "I2Cdev.h"
#include "ADXL345.h"

#include <ads1292r.h>
#include <SPI.h>
#include <MeetAndroid.h> //modified library that uses Serial1 instead of Serial!
#include "ClosedCube_MAX30205.h"


//--------------------------------------ADS1292R stuff----------------------------------

uint8_t DataPacketHeader[16];
ads1292r ADS1292;
//Packet format
#define  CES_CMDIF_PKT_START_1   0x0A
#define CES_CMDIF_PKT_START_2   0xFA
#define CES_CMDIF_TYPE_DATA   0x02
#define CES_CMDIF_PKT_STOP    0x0B

uint8_t data_len = 8;
unsigned long time;

volatile byte SPI_RX_Buff[150] ;
volatile static int SPI_RX_Buff_Count = 0;
volatile char *SPI_RX_Buff_Ptr;
volatile int Responsebyte = false;
volatile unsigned int pckt = 0 , buff = 0, t = 0 , jone = 0, jtwo = 0;
volatile unsigned long int RESP_Ch1_Data[150], ECG_Ch2_Data[150];
volatile unsigned char datac[150];
unsigned long uecgtemp = 0, Pkt_Counter = 0;
long ECG, RESP = 0;
signed long secgtemp = 0;
volatile int i;
volatile long packet_counter = 0;
//--------------------------------------ADS1292R stuff----------------------------------

// class default I2C address is 0x53
// specific I2C addresses may be passed as a parameter here
// ALT low = 0x53 (default for SparkFun 6DOF board)
// ALT high = 0x1D
ADXL345 accel;
ClosedCube_MAX30205 max30205; //human temperature sensor connected over I2C
// declare MeetAndroid so that you can call functions with it
MeetAndroid meetAndroid;
int16_t ax, ay, az;


//pins for switching the start buttons on the medical gadgets

#define TRIG_RR 2 //has to be pulled to ground to start the CK-101
#define TRIG_PULSEOX 3 // has to be pulled to 3.3V to start the CMS50C
#define RST_PULSEOX 15

//packet management bytes for the Amarino Android app
char startFlag = 18;
char ack = 19;
char delimiter = 59; //';' in case we use more than 1 channel

//vars for RR
boolean isMeasuringRR = false;
boolean RRdoneByteCame = false;
boolean successfulRR = false;
boolean readSpecialPacket = false;
boolean errorRR = false;
char incomingRRbyte;
int byteCntRR = 0;
char pulseCurve_RR;
char cuffPressRR;
char systRR;
char diastRR;
char HR_RR;

//vars for pulseOx
char incomingPulseOxbyte;
unsigned int byteCntPulse = 0;
char pulseCurve_PulseOx = 0;
char SPO2;
char HR_PulseOx;
boolean lastBitPulseOx = 0; //the MSB of the heart rate byte is hidden in a different byte - the one that holds the bar graph data

//timer vars
unsigned long GSRTmr, temperatureTmr = 0;
unsigned long sampIntGSR=50000;//sample interval for GSR measurement, Accelerometer, impedance pneumography value
unsigned long sampIntTemp=500000;//sample interval for the temperature sensor

unsigned long RRtrigTmr=0;
unsigned long RRtrigInt=200000; // length of the pulse that triggers the CK-101 button via NPN transistor
unsigned long pulseOxTrigTmr=0;
unsigned long pulseOxTrigInt=600000; // length of the pulse that triggers the CMS50C
unsigned long pulseOxRstTmr=0;
unsigned long pulseOxRstInt=800000;
boolean triggered = false;
boolean triggeredOx = false;
boolean resettedOx = false;
//----------------------------------------------------------------------------------------------------

void setup() {
  // initialize serial:
  Serial1.begin(115200); // Bluetooth port
  Serial2.begin(9600); // Blood pressure monitor CK-101 port
  Serial3.begin(4800, SERIAL_8E1); // Pulse oximeter CONTEC CMS 50C, annoying fact: it has EVEN PARITY!

  // initalize the  data ready and chip select pins for ADS1292R:
  pinMode(ADS1292_DRDY_PIN, INPUT);  //6
  pinMode(ADS1292_CS_PIN, OUTPUT);    //7
  pinMode(ADS1292_START_PIN, OUTPUT);  //5
  pinMode(ADS1292_PWDN_PIN, OUTPUT);  //4

  // make the pins outputs:
  pinMode(RST_PULSEOX, OUTPUT);
  pinMode(TRIG_RR, OUTPUT);
  pinMode(TRIG_PULSEOX, OUTPUT);
  
  digitalWrite(RST_PULSEOX, HIGH);
  digitalWrite(TRIG_RR, LOW);
  
  triggerOx(); //turn on the PulseOx
  // join I2C bus (I2Cdev library doesn't do this automatically)
  Wire.begin();
  accel.initialize();
  max30205.begin(0x48); //initialize the temp sensor
  //initalize ADS1292 slave
  ADS1292.ads1292_Init();
  //ADS1292.ads1292_Reset();
  // register callback functions, which will be called when an associated event occurs.
  meetAndroid.registerFunction(measureRR, 'b');


  GSRTmr=micros(); //GSR, RESP, ACC are sampled simultaneously
  temperatureTmr=micros(); //temperature is sampled slower
}
//----------------------------------------------------------------------------------------------------

void loop() {

//waiting to pull TRIG_RR LOW again to turn the NPN transistor off
  if((micros()-RRtrigTmr)>RRtrigInt && triggered == true){
    digitalWrite(TRIG_RR, LOW);
    triggered=false;
  }

  //waiting to pull TRIG_PULSEOX LOW again 
  if((micros()-pulseOxTrigTmr)>pulseOxTrigInt && triggeredOx == true){
    digitalWrite(TRIG_PULSEOX, LOW);
    triggeredOx=false;
  }
  
 //waiting pulseOxRstInt long after Reset and then trigger the Oximeter to turn it on again 
 //probably not necessary
  if((micros()-pulseOxRstTmr)>pulseOxRstInt && resettedOx == true){
     byteCntPulse=0; //clean up after reset
    resettedOx=false;
    triggerOx();
  }
  
/*
  //waiting to pull RST_PULSEOX HIGH again 
  if((micros()-pulseOxRstTmr)>pulseOxRstInt && resettedOx == true){
    digitalWrite(RST_PULSEOX, HIGH);
    resettedOx=false;
//after reset push its button
    digitalWrite(TRIG_PULSEOX, HIGH); //push its button
           pulseOxTrigTmr=micros();
           triggeredOx=true;
  }

  */
  
  sampleTemperature();

  sampleDevices();

  meetAndroid.receive(); // you need to keep this in your loop() to receive events

  readADS();

  readPulseOx();

  readBloodPress();

}

//------------------------------------functions for the components--------------------------------------------------

void sampleDevices(){
  //sample GSR, Respiration, Accelerometer
  if((micros() - GSRTmr) > sampIntGSR){
  Serial1.print(startFlag);
  Serial1.print("G");
  //Serial1.print(delimiter);
  Serial1.print(analogRead(A0), DEC);
  Serial1.print(delimiter);
  Serial1.print(RESP, DEC);
  //Serial1.print(ack);

  // read raw accel measurements from device
  accel.getAcceleration(&ax, &ay, &az);
  //Serial1.print(startFlag);
  //Serial1.print("A");
  Serial1.print(delimiter);
  Serial1.print(ax, DEC);
  Serial1.print(delimiter);
  Serial1.print(ay, DEC);
  Serial1.print(delimiter);
  Serial1.print(az, DEC);
  Serial1.print(ack);
  GSRTmr=micros();
  }
}

void sampleTemperature(){
//sample temperature
  if((micros()-temperatureTmr) > sampIntTemp){
  Serial1.print(startFlag);
  Serial1.print("T");
 // Serial1.print(delimiter);
  Serial1.print(max30205.readTemperature());
  Serial1.print(ack);
  temperatureTmr=micros();
  }
  
}

void readADS() {
  //----------------------------------ADS1292R code-------------------------------------------------

  if ((digitalRead(ADS1292_DRDY_PIN)) == LOW)
  {
    SPI_RX_Buff_Ptr = ADS1292.ads1292_Read_Data();
    Responsebyte = true;
    //Serial1.print("DRDY low: ");
  }

  if (Responsebyte == true)
  {
    /*Serial1.print("start Time: ");
      time = millis();
      //prints time since program started
      Serial1.println(time);
    */
    for (i = 0; i < 9; i++)
    {
      SPI_RX_Buff[SPI_RX_Buff_Count++] = *(SPI_RX_Buff_Ptr + i);
    }
    Responsebyte = false;
  }

  if (SPI_RX_Buff_Count >= 9)
  {
    pckt = 0; jone = 0;   jtwo = 0;
    for (i = 3; i < 9; i += 9)
    {
      //udi_cdc_putc(SPI_RX_Buff[i]);
      //RESP_Ch1_Data[jone++]=  SPI_RX_Buff[i+0];
      //RESP_Ch1_Data[jone++]= SPI_RX_Buff[i+1];
      //RESP_Ch1_Data[jone++]= SPI_RX_Buff[i+2];

      ECG_Ch2_Data[jtwo++] = (unsigned char)SPI_RX_Buff[i + 3];
      ECG_Ch2_Data[jtwo++] = (unsigned char)SPI_RX_Buff[i + 4];
      ECG_Ch2_Data[jtwo++] = (unsigned char)SPI_RX_Buff[i + 5];

      RESP_Ch1_Data[jone++] = (unsigned char)SPI_RX_Buff[i + 0];
      RESP_Ch1_Data[jone++] = (unsigned char)SPI_RX_Buff[i + 1];
      RESP_Ch1_Data[jone++] = (unsigned char)SPI_RX_Buff[i + 2];
    }

    packet_counter++;

    uecgtemp = (unsigned long) ((ECG_Ch2_Data[0] << 16) | (ECG_Ch2_Data[1] << 8) | ECG_Ch2_Data[2]);
    uecgtemp = (unsigned long) (uecgtemp << 8);
    //secgtemp = (signed long) (uecgtemp);
    //secgtemp = (signed long) (secgtemp>>8);
    ECG = uecgtemp + 134217728; //(unsigned int) ((ECG_Ch2_Data[0]<<16)|(ECG_Ch2_Data[1]<<8)|ECG_Ch2_Data[2]);//uecgtemp;//map(uecgtemp,0,4294967296,0,65535);
    ECG = ECG/16;
    //if(ECG>4194304 || ECG<12582912)
    //ECG=map(ECG,4194304,12582912,0,16777216);
    
    Serial1.print(startFlag);
    Serial1.print("E");
    //Serial1.print(delimiter);
    Serial1.print(ECG, DEC);
    Serial1.print(ack);
    uecgtemp = (unsigned long) ((RESP_Ch1_Data[0] << 16) | (RESP_Ch1_Data[1] << 8) | RESP_Ch1_Data[2]);
    uecgtemp = (unsigned long) (uecgtemp << 8);
    RESP = uecgtemp + 134217728; //(unsigned int) ((RESP_Ch1_Data[0]<<16)|(RESP_Ch1_Data[1]<<8)|RESP_Ch1_Data[2]);
    RESP=RESP/256;
    //RESP = map(RESP, 0, 8388608, 0, 9999);
    //secgtemp = (signed long) (uecgtemp);
    //secgtemp = (signed long) (secgtemp>>8);
  }
  SPI_RX_Buff_Count = 0;
}

void readBloodPress() {

  //--------------------Blood pressure data analysis----------------

  if (Serial2.available() > 0) {
    //RR data
    incomingRRbyte = Serial2.read();

    switch (byteCntRR) {
      case 0:

        if (readSpecialPacket == false && successfulRR == false) {
          Serial1.print(startFlag);
          Serial1.print("r");
          //Serial1.print(delimiter);
          Serial1.print(cuffPressRR, DEC); //send the pulse curve value
          Serial1.print(delimiter);
          Serial1.print(pulseCurve_RR, DEC); //send the heart rate
          Serial1.print(ack);
        }
        break;

      case 1:
        if (readSpecialPacket == false) {
          cuffPressRR = incomingRRbyte;
          isMeasuringRR = true;

        }

        if (incomingRRbyte == 8) { //if the third byte after 255 254 is 008 we had a successful measurement!
          isMeasuringRR = false;
        }
        if (incomingRRbyte == 3) { //if the third byte after 255 254 is 003 - it's the beginning sequence!


        }
        if (incomingRRbyte == 4) { //oh no, measurement was not successful!

          errorRR = true;

        }
        byteCntRR++;

        break;

      case 2:
        if (readSpecialPacket == false) {
          pulseCurve_RR = incomingRRbyte;
          successfulRR = false;
          byteCntRR = 0;
        } else {
          byteCntRR++;


        }

        if (incomingRRbyte == 83) { //beginning sequence

        }
        if (incomingRRbyte == 93) { //error sequence

        }
        if (incomingRRbyte == 98) { //if measurement was successful

        }
        break;

      case 3: //dunno what this byte means yet
        if (incomingRRbyte == 80) { //beginning sequence
          byteCntRR = 0; //entry sequence ends after this byte
          readSpecialPacket = false;
          isMeasuringRR = true;
          successfulRR = false;
          // Serial1.print("Begun");
        } else {
          byteCntRR++;

        }
        if (incomingRRbyte == 86) { //error sequence

        }
        if (incomingRRbyte == 85) { //if measurement was successful

        }
        //if you are not in special packet mode stop here
        if (readSpecialPacket == false)
          byteCntRR = 0;
        break;

      case 4:
        if (incomingRRbyte == 3) { //error sequence
          byteCntRR = 0; //error sequence ends here
          readSpecialPacket = false;
          isMeasuringRR = false;
          Serial1.print(startFlag);
          Serial1.print("RRerr");
          Serial1.print(ack);

          digitalWrite(RST_PULSEOX, HIGH);
pulseOxRstTmr=micros();
   resettedOx=true;
        } else
          byteCntRR++;
        if (incomingRRbyte == 0) { //if measurement was successful

        }

        break;

      case 5: // syst. low byte
        systRR = incomingRRbyte;
        byteCntRR++;

        break;
      case 6:
        //syst. high byte
        systRR = 256 * incomingRRbyte + systRR; //in case anyone has a blood pressure above 255 mmHg!
        byteCntRR++;
        break;

      case 7: //diastolic pressure
        diastRR = incomingRRbyte;
        byteCntRR++;
        break;

      case 8: //heart rate measured by the blood pressure monitor
        HR_RR = incomingRRbyte;

        successfulRR = true;
        byteCntRR = 0;
        readSpecialPacket = false;

        if (successfulRR) {
          Serial1.print(startFlag);
          Serial1.print("R");
          //Serial1.print(delimiter);
          Serial1.print(systRR, DEC);
          Serial1.print(delimiter);
          Serial1.print(diastRR, DEC);
          Serial1.print(delimiter);
          Serial1.print(HR_RR, DEC);
          Serial1.print(ack);
digitalWrite(RST_PULSEOX, HIGH);
pulseOxRstTmr=micros();
   resettedOx=true;
          //  triggerOx();
          
//digitalWrite(RST_PULSEOX, LOW);
//resettedOx=true;
 //pulseOxRstTmr=micros();      
        }

        break;

      default:
        break;
    }

    if (incomingRRbyte == 255 ) { //if during measurement 255 appears as a pressure value, this is not supposed to trigger!!!!
      if (byteCntRR == 0 && isMeasuringRR == false)
        RRdoneByteCame = true;
      //Serial1.print("255came");
    }

    if ( incomingRRbyte == 254 ) {
      byteCntRR++;
      if (RRdoneByteCame == true) {
        readSpecialPacket = true;
        RRdoneByteCame = false;
      }

    }

  }
}

void readPulseOx() {
  if (successfulRR) digitalWrite(RST_PULSEOX, HIGH);
  //--------------------Pulse Oximeter data analysis----------------
  if (Serial3.available() > 0) {
    //pulseox data
    incomingPulseOxbyte = Serial3.read();
    if (byteCntPulse == 1) {
      //the second byte contains a 7 bit pulse wave value, Fs=60Hz
      pulseCurve_PulseOx = incomingPulseOxbyte;
      byteCntPulse++;
    } else if (byteCntPulse == 2) {
      //the 0-3rd bits of the third byte is the bar graph value .
      //the 6th bit is bit 7 of the heart rate value!
      if ( bitRead(incomingPulseOxbyte, 6)) {
        //if the heart rate is >= 128, this happens
        lastBitPulseOx = 1;
      } else
        lastBitPulseOx = 0;

      byteCntPulse++; //move on
    } else if (byteCntPulse == 3) {
      //
      if (lastBitPulseOx)
        //if the heart rate is >= 128, this happens
        HR_PulseOx = 128 + incomingPulseOxbyte;
      else
        HR_PulseOx = incomingPulseOxbyte;

      byteCntPulse++;
    } else if (byteCntPulse == 4) {
      //the last byte contains the most important SPO2 value
      SPO2 = incomingPulseOxbyte;
      byteCntPulse = 0; //we're done the data packet has 5 bytes in total
    } else {


      Serial1.print(startFlag);
      Serial1.print("P");
      //Serial1.print(delimiter);
      Serial1.print(pulseCurve_PulseOx, DEC); //send the pulse curve value
      Serial1.print(delimiter);
      Serial1.print(HR_PulseOx, DEC); //send the heart rate
      Serial1.print(delimiter);
      Serial1.print(SPO2, DEC); //send SPO2 value!
      Serial1.print(ack);

    }

    //analyze the first byte. it has plenty of information about the data
    //the fourth bit of the first packet byte means "OK signal" if zero, otherwise it stands for "searching too long"
    //the seventh bit of the first byte has to be always set!!!!! (the only byte where this is the case) all other bytes have a bit 7 == 0
    if ( bitRead(incomingPulseOxbyte, 7)) {
      byteCntPulse++;
    }
  }

}

//this gets executed after the android device has sent you a prompt
void measureRR(byte flag, byte numOfValues) {
 triggerRR();
 //the reset pin of the MSP430 inside the pulse oximeter is held low throughout the measurement process of the RR monitor
  //reset the pulse oximeter after/during the measurement because otherwise it freaks out and thinks you're dead due to the arteries getting compressed by the blood pressure monitor and the pulses disappearing
  digitalWrite(RST_PULSEOX, LOW);
  
}

void triggerOx(){
//function to trigger the pulse oximeter button
   digitalWrite(TRIG_PULSEOX,HIGH);
   pulseOxTrigTmr=micros();
   triggeredOx=true;
 }

void triggerRR(){
//trigger the blood pressure monitor button
   digitalWrite(TRIG_RR, HIGH);
  RRtrigTmr=micros();
  triggered=true;
}



