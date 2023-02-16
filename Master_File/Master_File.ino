#include <HID.h>

#include <Adafruit_BLEBattery.h>
#include <Arduino.h>
#include <SPI.h>
#include "Adafruit_BLE.h"
//#include "Adafruit_BluefruitLE_SPI.h"
#include "Adafruit_BluefruitLE_UART.h"

#include "BluefruitConfig.h"
//
//#if SOFTWARE_SERIAL_AVAILABLE
//  #include <SoftwareSerial.h>
//#endif

/*=========================================================================
    APPLICATION SETTINGS

    FACTORYRESET_ENABLE       Perform a factory reset when running this sketch
   
                              Enabling this will put your Bluefruit LE module
                              in a 'known good' state and clear any config
                              data set in previous sketches or projects, so
                              running this at least once is a good idea.
   
                              When deploying your project, however, you will
                              want to disable factory reset by setting this
                              value to 0.  If you are making changes to your
                              Bluefruit LE device via AT commands, and those
                              changes aren't persisting across resets, this
                              is the reason why.  Factory reset will erase
                              the non-volatile memory where config data is
                              stored, setting it back to factory default
                              values.
       
                              Some sketches that require you to bond to a
                              central device (HID mouse, keyboard, etc.)
                              won't work at all with this feature enabled
                              since the factory reset will clear all of the
                              bonding data stored on the chip, meaning the
                              central device won't be able to reconnect.
    MINIMUM_FIRMWARE_VERSION  Minimum firmware version to have some new features
    MODE_LED_BEHAVIOUR        LED activity, valid options are
                              "DISABLE" or "MODE" or "BLEUART" or
                              "HWUART"  or "SPI"  or "MANUAL"
    -----------------------------------------------------------------------*/
    #define FACTORYRESET_ENABLE         1
    #define MINIMUM_FIRMWARE_VERSION    "0.6.6"
    #define MODE_LED_BEHAVIOUR          "MODE"
/*=========================================================================*/

// Create the bluefruit object, either software serial...uncomment these lines
/*
SoftwareSerial bluefruitSS = SoftwareSerial(BLUEFRUIT_SWUART_TXD_PIN, BLUEFRUIT_SWUART_RXD_PIN);

Adafruit_BluefruitLE_UART ble(bluefruitSS, BLUEFRUIT_UART_MODE_PIN,
                      BLUEFRUIT_UART_CTS_PIN, BLUEFRUIT_UART_RTS_PIN);
*/

/* ...or hardware serial, which does not need the RTS/CTS pins. Uncomment this line */
Adafruit_BluefruitLE_UART ble(Serial, BLUEFRUIT_UART_MODE_PIN);

#include "Servo.h"

// Pin 1 is thumb, 2 is pointer, etc.
#define SERVO_PIN1 9   // PWM Motor 1 Pin
#define SERVO_PIN2 10  // PWM Motor 2 Pin
#define SERVO_PIN3 3   // PWM Motor 3 Pin
#define SERVO_PIN4 5   // PWM Motor 4 Pin
#define SERVO_PIN5 6   // PWM Motor 5 Pin 

#define FLEX_PIN1 A1 // Flex sensor 1
#define FLEX_PIN2 A2 // Flex sensor 2
#define FLEX_PIN3 A3 // Flex sensor 3
#define FLEX_PIN4 A4 // Flex sensor 4
#define FLEX_PIN5 A5 // Flex sensor 5

#define MIN_VALUE 0   // Minimum Servo position
#define MAX_VALUE 180 // Maximum Servo position

Servo servo1;
Servo servo2;
Servo servo3;
Servo servo4;
Servo servo5;

int TI = 0;
int MRP = 1023;
int value_setTI = TI;
int value_servoTI = TI; // servo analog value
int value_servo_zeroTI = map(value_setTI, 0, 1023, MIN_VALUE, MAX_VALUE);
int value_setMRP = MRP;
int value_servoMRP = MRP;
int value_servo_zeroMRP = map(value_setMRP, 0, 1023, MIN_VALUE, MAX_VALUE); // mapping the value set for the servo motors
int motorSpeed = 0;
bool stopFromApp = 0;
bool stopMotors = 0;

int positionReported;

int count = 0;
bool startMotors = 0;

// Using a 32 max int, we will have a 5 bit system to tell which motors we want running. DECODE THIS INT
unsigned int motorSelect = 0;
unsigned long previousMillis = 0;
unsigned long interval = 1000;

// 500 is fastest, 1000 is slowest
// 100 ---- 0

// A couple global variables to keep track of time
unsigned long StartTime;
unsigned long TimeReference;


// Read IDs
int32_t readServiceId;
int32_t thumbCharID;
int32_t indexCharID;
int32_t middleCharID;
int32_t ringCharID;
int32_t pinkyCharID;
int32_t donePerfCharID;
int32_t extraCharID;

// Write IDs
int32_t writeServiceId;
int32_t motorCharId;
int32_t speedCharId;
int32_t positionCharId;
int32_t doneFromeAppCharId;

int tickValue = 0;

// Values
double thumb = 0;
double ind = 0;
double middle = 0;
double ring = 0;
double pinky = 0;
double extra = 0; // extra room
double done = 0;

double checkThumb = 0;
double checkIndex = 0;
double checkMiddle = 0;
double checkRing = 0;
double checkPinky = 0;

// VALUES FOR MOTOR, SPEED, AND POSITION NEED GO HERE


/* Function: error
 * ---------------------------------
 * small helper function
 * 
 * returns: n/a - void. Error message is printed serially
 */
void error(const __FlashStringHelper*err) {
  Serial.println(err);
  while (1);
}


/* Function: updatedoubleCharacteristic
 * ---------------------------------
 * This function updates given doubleeger characteristics and emitts
 * the updated values via the BLE module
 * 
 * nameOfChar = the name of the characteristic to be emitted as a String
 * characteristic = the value of the characteristic - must be an doubleeger
 * serviceId = the id that this characteristic belongs to
 * 
 * returns: n/a - void
 */
void updatedoubleCharacteristic(String nameOfChar, double characteristic, int32_t charId) {
/*
  .print("Byte size of ");
  .print(nameOfChar);
  .print(" : ");
  .println(sizeof(characteristic));

  .print("CharID of ");
  .print(nameOfChar);
  .print(" : ");
  Serial.println(String(charId));
*/    
  ble.print( F("AT+GATTCHAR=") );
  ble.print( charId );
  ble.print( F(",") );
  ble.println(String(characteristic)); // Characteristic is the value. SENDS TO APP
/*
  Serial.print("Actual value of ");
  Serial.print(nameOfChar);
  Serial.print(" : ");
  Serial.println(characteristic);
*/  
  if ( !ble.waitForOK() ) Serial.println(F("Failed to get response!")); // Waits to receive value
/*
  Serial.println("");
  Serial.println("");
*/  
}

void readFromApp(int32_t charId) {
  ble.print( F("AT+GATTCHAR=") );
  ble.println( charId ); // motors, stop, etc.
  String r;
  String s;
  //s=ble.read();
  s=ble.readline();
  r=ble.buffer; // value that we're reading in, the INT read value. FROM APP TO HER/E

    if(r.toInt()==0 && motorCharId==charId){
      stopMotors = 1;
    }
//  switch(charId){
    if(motorCharId==charId){
      motorSelect = r.toInt();
      stopMotors = 0;
    }
    if(speedCharId==charId){
      motorSpeed = r.toInt();
      if(motorSpeed<=100 && motorSpeed>81){
        interval=0;
      } else if(motorSpeed<=81 && motorSpeed>61){
        interval=250;
      } else if(motorSpeed<=61 && motorSpeed>41){
        interval=500;
      } else if(motorSpeed<=41 && motorSpeed>21) {
        interval=750;
      } else if(motorSpeed<=21 && motorSpeed>0){
        digitalWrite(7, HIGH);
        interval=1000;
      }
      stopMotors = 0;
    }
    if(positionCharId==charId){
      positionReported = r.toInt();
      stopMotors = 0;
    }
    if(doneFromeAppCharId==charId){
      stopFromApp  = r.toInt();
      stopMotors = 0;
    }
  //r=ble.buffer; // value that we're reading in, the INT read value. FROM APP TO HER/E
//  if (strcmp(ble.buffer, "OK") == 0) {
//      Serial.print("\nNo data\n");
//      return;
//  }
//  Serial.print(F("\nBufferVal: ")); 
//  Serial.println(r);
//  Serial.print(F("ReadVal: ")); 
//  Serial.println(s); // Not important, just don't touch
//
//    // Hex output too, helps w/debugging!
//    Serial.print("Hex [0x");
//    if (s.toInt() <= 0xF) Serial.print(F("0"));
//    Serial.print(s.toInt(), HEX);
//    Serial.print("] \n\n");
  
  if ( !ble.waitForOK() ) Serial.println(F("Failed to get response!"));
  
}


/* Function: emittPeripheralData
 * ---------------------------------
 * prepares sensor data and passes it along to 
 * be emitted by the BLE module
 * Calls the characteristics 6 times. Once for each value
 * returns: n/a -
 */
void emittPeripheralData(double thumb, double ind, double middle, double ring, double pinky, double extra, double done) {
  updatedoubleCharacteristic("thumb sensor", thumb, thumbCharID);
  updatedoubleCharacteristic("index sensor", ind, indexCharID);
  updatedoubleCharacteristic("middle sensor", middle, middleCharID);
  updatedoubleCharacteristic("ring sensor", ring, ringCharID);
  updatedoubleCharacteristic("pinky sensor", pinky, pinkyCharID);
  updatedoubleCharacteristic("extra sensor", done, donePerfCharID);
}

void setup() {
  pinMode(7, OUTPUT);    
  digitalWrite(7, HIGH);
  // Connects servo object to the motor pins
  servo1.attach(SERVO_PIN1); // assigns PWM pin to the servo object
  servo2.attach(SERVO_PIN2); // assigns PWM pin to the servo object
  servo3.attach(SERVO_PIN3); // assigns PWM pin to the servo object
  servo4.attach(SERVO_PIN4); // assigns PWM pin to the servo object
  servo5.attach(SERVO_PIN5); // assigns PWM pin to the servo object
  // SETUP BLE
  delay(500);
  boolean success;
  Serial.begin(9600); // May need to change to 96000 later.
  if ( !ble.begin(false) ) ; // Set to false for silent and true for debug
  delay(1000);
  if (! ble.factoryReset() ) ;
  ble.echo(false);
  ble.info();
  // this line is particularly required for Flora, but is a good idea
  // anyways for the super long lines ahead!
   //ble.setInterCharWriteDelay(5); // 5 ms
  //Setup name, needs to havs same name as application ID or won't show in app
delay(500);
  if (! ble.sendCommandCheckOK(F("AT+GAPDEVNAME=Rehab Glove")) ) ;
delay(500);
  // TAKES VALUES FROM FLEX TO APP
  //Setup fingers and peripheral-side chars --- 2 IS READ, 8 IS WRITE. VALUE IS INTIIALIZED VALUE. DATATYPE DETERMINES WHAT'S COMING IN. 1 IS STRING
  // 3 IS INT, 2 IS BYTEARRAY, 0 IS AUTO
  // Services
  success = ble.sendCommandWithIntReply( F("AT+GATTADDSERVICE=UUID128=00-00-00-01-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-66"), &readServiceId);
  if (! success) error(F("Could not add read service"));
delay(500);
  // Adds characteristics
  success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID128=00-00-00-02-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-66, PROPERTIES=0x2, MIN_LEN=1, MAX_LEN=20,VALUE=5,DATATYPE=1"), &thumbCharID);
  if (! success) error(F("Could not add thumb measurement characteristic"));
delay(500);  
  success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID128=00-00-00-03-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-66, PROPERTIES=0x2, MIN_LEN=1, MAX_LEN=20,VALUE=5,DATATYPE=1"), &indexCharID);
  if (! success) error(F("Could not add index measurement characteristic"));
delay(500);  
  success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID128=00-00-00-04-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-66, PROPERTIES=0x2, MIN_LEN=1, MAX_LEN=20,VALUE=5,DATATYPE=1"), &middleCharID);
  if (! success) error(F("Could not add middle measurement characteristic"));
delay(500);  
  success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID128=00-00-00-05-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-66, PROPERTIES=0x2, MIN_LEN=1, MAX_LEN=20,VALUE=5,DATATYPE=1"), &ringCharID);
  if (! success) error(F("Could not add ring measurement characteristic"));
delay(500);  
  success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID128=00-00-00-06-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-66, PROPERTIES=0x2, MIN_LEN=1, MAX_LEN=20,VALUE=5,DATATYPE=1"), &pinkyCharID);
  if (! success) error(F("Could not add pinky measurement characteristic"));
delay(500);  
  success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID128=00-00-00-07-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-66, PROPERTIES=0x2, MIN_LEN=1, MAX_LEN=20,VALUE=5,DATATYPE=1"), &donePerfCharID);
  if (! success) error(F("Could not add done from peripheral characteristic"));
delay(500);  
  success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID128=00-00-00-08-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-66, PROPERTIES=0x2, MIN_LEN=1, MAX_LEN=20,VALUE=5,DATATYPE=1"), &extraCharID);
  if (! success) error(F("Could not add extra measurement characteristic"));
delay(500);
  //Setup motor control and app-side chars
  // THE WRITE FROM APP TO MOTOR OF THINGS
  success = ble.sendCommandWithIntReply( F("AT+GATTADDSERVICE=UUID128=00-00-00-01-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-77"), &writeServiceId);
  if (! success) error(F("Could not add write service"));
delay(500);
  success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID128=00-00-00-02-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-77, PROPERTIES=0x08, MIN_LEN=1, MAX_LEN=20,VALUE=0,DATATYPE=3"), &motorCharId);
  if (! success) error(F("Could not add motor control characteristic"));
delay(500);
  success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID128=00-00-00-03-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-77, PROPERTIES=0x08, MIN_LEN=1, MAX_LEN=20,VALUE=0,DATATYPE=3"), &speedCharId);
  if (! success) error(F("Could not add speed control characteristic"));
delay(500);
  success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID128=00-00-00-04-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-77, PROPERTIES=0x08, MIN_LEN=1, MAX_LEN=20,VALUE=0,DATATYPE=3"), &positionCharId);
  if (! success) error(F("Could not add position control characteristic"));
delay(500);
    success = ble.sendCommandWithIntReply( F("AT+GATTADDCHAR=UUID128=00-00-00-05-62-7E-47-E5-A3-fC-DD-AB-D9-7A-A9-77, PROPERTIES=0x08, MIN_LEN=1, MAX_LEN=20,VALUE=0,DATATYPE=3"), &doneFromeAppCharId);
  if (! success) error(F("Could not add done from app characteristic"));
delay(500);

ble.reset();
  Serial.println();
  servo1.write(value_servo_zeroTI);
  servo2.write(value_servo_zeroTI);
  servo3.write(value_servo_zeroMRP);
  servo4.write(value_servo_zeroMRP);
  servo5.write(value_servo_zeroMRP);
  startMotors = 0;
  value_setTI=TI;
  value_setMRP = MRP;
  count = 0;     
  digitalWrite(7, LOW);
}


void loop() {
  if(millis() - previousMillis > interval){

    if(motorSpeed!=0 && stopFromApp!=1){
//      count = motorSpeed+50;
      count = 56.833;
      if(value_setMRP>0+count){
        value_setTI+=count;
        value_setMRP-=count;
        tickValue++;
      }
    }
    
    if(tickValue>positionReported){
       resetMotors();
    }

    value_servoTI = map(value_setTI, 0, 1023, MIN_VALUE, MAX_VALUE); // mapping the value set for the servo motors
    value_servoMRP = map(value_setMRP, 0, 1023, MIN_VALUE, MAX_VALUE);
      if(stopFromApp==0 && motorSelect!=32){
        //digitalWrite(7, HIGH);
//        if(checkThumb>positionReported){
//          servo1.write(value_servo_zeroTI);
//        } else 
        if(bitRead(motorSelect,0)==1){
          servo1.write(value_servoTI);
        }else{
          //servo1.write(value_servo_zeroTI);
        }
//        if(checkIndex>positionReported){
//          servo2.write(value_servo_zeroTI);
//        } else 
        if(bitRead(motorSelect,1)==1){
          servo2.write(value_servoTI);
        }else{
          //servo2.write(value_servo_zeroTI);
        }
//        if(checkMiddle>positionReported){
//          servo3.write(value_servo_zeroMRP);
//        } else 
        if(bitRead(motorSelect,2)==1){
          servo3.write(value_servoMRP);
        }else{
          //servo3.write(value_servo_zeroMRP);
        }
//        if(checkRing>positionReported){
//          servo4.write(value_servo_zeroMRP);
//        }else 
        if(bitRead(motorSelect,3)==1){
          servo4.write(value_servoMRP);
        }else{
          //servo4.write(value_servo_zeroMRP);
        }
//        if(checkPinky>positionReported){
//          servo5.write(value_servo_zeroMRP);
//        } else 
        if(bitRead(motorSelect,4)==1){
          servo5.write(value_servoMRP);
        }else{
          //servo5.write(value_servo_zeroMRP);
        }
      }
 
    if(stopFromApp==1){
      servo1.write(value_servo_zeroTI);
      servo2.write(value_servo_zeroTI);
      servo3.write(value_servo_zeroMRP);
      servo4.write(value_servo_zeroMRP);
      servo5.write(value_servo_zeroMRP);
      startMotors = 0;
      value_setTI = TI;
      value_setMRP = MRP;
      count = 0;
      tickValue=0;
    }
      
    previousMillis+=interval;
  }


  thumb = analogRead(FLEX_PIN1)*(5.0 / 1024.0); // Set each of these to whatever drives flex sensors. SEND RAW DATA. RAWWWW. READIN VALUE
  ind = analogRead(FLEX_PIN2)*(5.0 / 1024.0);
  middle = analogRead(FLEX_PIN3)*(5.0 / 1024.0);
  ring = analogRead(FLEX_PIN4)*(5.0 / 1024.0);
  pinky = analogRead(FLEX_PIN5)*(5.0 / 1024.0);

  emittPeripheralData(thumb,ind,middle,ring,pinky,extra,done);
  readFromApp(motorCharId);
  readFromApp(speedCharId);
  readFromApp(positionCharId);
  readFromApp(doneFromeAppCharId);
}

void resetMotors(){
  digitalWrite(7, LOW);
  servo1.write(value_servo_zeroTI);
  servo2.write(value_servo_zeroTI);
  servo3.write(value_servo_zeroMRP);
  servo4.write(value_servo_zeroMRP);
  servo5.write(value_servo_zeroMRP);
  startMotors = 0;
  value_setTI=TI;
  value_setMRP=MRP;
  count = 0;
  stopFromApp = 0;
  tickValue=0;
}

//void resetMotorSelect(int x){
//  switch(bitRead(x)){
//    case 1:
//      servo1.write(1023);
//      break;
//    case 2:
//      servo2.write(1023);
//      break;
//    case 3:
//      servo3.write(0);
//      break;
//    case 4:
//      servo4.write(0);
//      break;
//    case 5:
//      servo5.write(0);
//      break;
//  }
//}

bool checkFlex(int x){
  double checkValue = 0.0024*pow(x,3)-0.0539*pow(x,2)+0.063*x+4.2207;

  if(abs(checkValue-positionReported)>1){
    return true;
  } else {
    return false;
  }
}

int checkAllFlex(){
  int fails=0;
  checkThumb = (-11.8725*pow(thumb,3))+(83.643*pow(thumb,2))+(-236.0979*thumb)+380.2074;
  if(abs((checkThumb*5.6833)-value_setTI)>56.833){
    fails++;
  }
  checkIndex = -175.3966*ind+712.1525;
    if(abs((checkIndex*5.6833)-value_setTI)>56.833){
    fails++;
  }
  checkMiddle = -261.2576*middle+1.1419E+3;
    if(abs((checkMiddle*5.6833)-value_setTI)>56.833){
    fails++;
  }
  checkRing = -158.120134905516*ring+665.617500915603;
    if(abs((checkRing*5.6833)-value_setTI)>56.833){
    fails++;
  }
  checkPinky = (-9.7278*pow(pinky,3))+(116.477*pow(pinky,2))+(-479.5077*pinky)+685.3639;
    if(abs((checkPinky*5.6833)-value_setTI)>56.833){
    fails++;
  }
    return fails;
}
