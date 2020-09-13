#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <Wire.h>
#include <Adafruit_PWMServoDriver.h>
#include <HX711.h>

// HX711 circuit wiring
const int LOADCELL_DOUT_PIN = D3;
const int LOADCELL_SCK_PIN = D4;

HX711 scale;

// called this way, it uses the default address 0x40
Adafruit_PWMServoDriver pwm = Adafruit_PWMServoDriver();

#define SERVO_FREQ 50 // Analog servos run at ~50 Hz updates
#define MIN_PULSE_WIDTH       650
#define MAX_PULSE_WIDTH       2350
#define DEFAULT_PULSE_WIDTH   1500



const char* ssid = "Joao Vitor";
const char* password = "mj110032";
const char* mqtt_server = "192.168.0.25";

WiFiClient espClient;
PubSubClient client(espClient);
unsigned long lastMsg = 0;
unsigned long lastMotor = 0;
#define MSG_BUFFER_SIZE	(50)
char msg[MSG_BUFFER_SIZE];
int value = 0;
int iMotor = 0;

String topicConfRot = "topicConfRot";
String topicRotAtual = "topicRotAtual";
String topicStatMotor = "topicStatMotor";
String strLigar = "ligar";
String strDesligar = "desligar";

String inBuff;

int rotMax;

int pulseWidth(int angle)
{
    int pulse_wide, analog_value;
    pulse_wide   = map(angle, 0, 180, MIN_PULSE_WIDTH, MAX_PULSE_WIDTH);
    analog_value = int(float(pulse_wide) / 1000000 * SERVO_FREQ * 4096);
    return analog_value;
}

void setup_wifi() {

    delay(10);
    // We start by connecting to a WiFi network
    Serial.println();
    Serial.print("Connecting to ");
    Serial.println(ssid);

    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid, password);

    while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(500);
    }

    randomSeed(micros());

    Serial.println("");
    Serial.println("WiFi connected");
    Serial.println("IP address: ");
    Serial.println(WiFi.localIP());
}

void callback(char* topic, byte* payload, unsigned int length) {
 
  if(topicConfRot.equals(topic)){
    for (int i = 0; i < length; i++) {
      inBuff.concat((char)payload[i]);
    }
    iMotor = inBuff.toInt();
    inBuff = "";
  }else if(topicStatMotor.equals(topic)){
    for (int i = 0; i < length; i++) {
      inBuff.concat((char)payload[i]);
    }
    if(strLigar.equals(inBuff)){
      digitalWrite(D8, HIGH);
    }else if(strDesligar.equals(inBuff)){
      digitalWrite(D8, LOW);
    }else{
      digitalWrite(D8, LOW);;
    }
    inBuff = "";
  }
}

void reconnect() {
  // Loop until we're reconnected
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    // Create a random client ID
    String clientId = "ESP8266Client-";
    clientId += String(random(0xffff), HEX);
    // Attempt to connect
    if (client.connect(clientId.c_str(), "tht", "123")) {
      Serial.println("connected");
      // ... and resubscribe
      client.subscribe("topicConfRot");
      client.subscribe("topicStatMotor");
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      // Wait 5 seconds before retrying
      delay(5000);
    }
  }
}

void setup() {
    pinMode(D0, OUTPUT);     // Initialize the BUILTIN_LED pin as an output

    pinMode(D8, OUTPUT); 
    digitalWrite(D8, LOW);
    Serial.begin(9600);
    setup_wifi();
    client.setServer(mqtt_server, 1883);
    client.setCallback(callback);

    pwm.begin();
    //pwm.setOscillatorFrequency(27000000);
    pwm.setPWMFreq(SERVO_FREQ);  // Analog servos run at ~50 Hz updates

    scale.begin(LOADCELL_DOUT_PIN, LOADCELL_SCK_PIN);
}

void loop() {

    if (!client.connected()) {
        reconnect();
    }
    client.loop();

    unsigned long now = millis();
    if (now - lastMotor > 400) {
        lastMotor = now;
        pwm.setPWM( 0, 0, pulseWidth(iMotor));
        snprintf (msg, MSG_BUFFER_SIZE, "%d", iMotor);
        client.publish("topicRotAtual", msg);

        if (scale.wait_ready_retry(10)) {
            long reading = scale.read();
            long result = (reading - 7000) /100;
            snprintf (msg, MSG_BUFFER_SIZE, "%d", result);
            client.publish("topicEnpuxo", msg);
        } 
    }
}