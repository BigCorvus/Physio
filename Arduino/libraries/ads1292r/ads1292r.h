/*
  ADS1292R.h - Library for ADS1292R Shield Arduino Firmwar.
  Created by Protocentral, December 27, 2013.
  Released into the public domain.
*/

#ifndef ads1292r_h
#define ads1292r_h

#include "Arduino.h"

#define CONFIG_SPI_MASTER_DUMMY   0xFF

// Register Read Commands
#define  RREG    0x20;		//Read n nnnn registers starting at address r rrrr
                                //first byte 001r rrrr (2xh)(2) - second byte 000n nnnn(2)
#define WREG    0x40;		//Write n nnnn registers starting at address r rrrr
                                //first byte 010r rrrr (2xh)(2) - second byte 000n nnnn(2)

#define START		0x08		//Start/restart (synchronize) conversions
#define STOP		0x0A		//Stop conversion
#define RDATAC      0x10		//Enable Read Data Continuous mode. 

//This mode is the default mode at power-up.
#define SDATAC		0x11		//Stop Read Data Continuously mode
#define RDATA		0x12		//Read data by command; supports multiple read back.

//Pin declartion the other you need are controlled by the SPI library
//changed for teensy 3.2
const int ADS1292_DRDY_PIN = 6;
const int ADS1292_CS_PIN = 5;
const int ADS1292_START_PIN = 23;
const int ADS1292_PWDN_PIN = 4;

//register address
#define ADS1292_REG_ID			0x00
#define ADS1292_REG_CONFIG1		0x01
#define ADS1292_REG_CONFIG2		0x02
#define ADS1292_REG_LOFF		0x03
#define ADS1292_REG_CH1SET		0x04
#define ADS1292_REG_CH2SET		0x05
#define ADS1292_REG_RLDSENS		0x06
#define ADS1292_REG_LOFFSENS    0x07
#define ADS1292_REG_LOFFSTAT    0x08
#define ADS1292_REG_RESP1	    0x09
#define ADS1292_REG_RESP2	    0x0A

class ads1292r
{
  public:
    static void ads1292_Init(void);
    static void ads1292_Reset(void);
	static void ads1292_Reg_Write (unsigned char READ_WRITE_ADDRESS, unsigned char DATA);
	static void ads1292_Reg_Read (unsigned char READ_WRITE_ADDRESS);
    static void ads1292_SPI_Command_Data(unsigned char data_in);
    static void ads1292_Disable_Start(void);
    static void ads1292_Enable_Start(void);
    static void ads1292_Hard_Stop (void);
    static void ads1292_Start_Data_Conv_Command (void);
    static void ads1292_Soft_Stop (void);
    static void ads1292_Start_Read_Data_Continuous (void);
    static void ads1292_Stop_Read_Data_Continuous (void);
    static char* ads1292_Read_Data(void);

};

#endif

