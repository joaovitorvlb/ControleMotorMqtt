package com.example.appcontrolemotor;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.UnsupportedEncodingException;

public class MainActivity extends AppCompatActivity {

    // Declarações de Campos EditText
    private EditText UMaxima, UMinima;
    private ProgressBar pB ;
    private ImageView iV;
    private TextView uText;
    private TextView txtTeste;
    private TextView txtTeste2;

    // Declaração para as mensagens de depuração
    private static final String TAG = MainActivity.class.getSimpleName();

    // Declarações e inicializações para o MQTT
    private String clientId = MqttClient.generateClientId();
    private MqttAndroidClient client;
    boolean assinou = false;

    // Declarações e inicializações para as notificações
    private NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
    private TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
    private NotificationManager mNotificationManager;
    private int cont = 0;

    private MqttCallback ClientCallBack = new MqttCallback() {
        @Override
        public void connectionLost(Throwable cause) {
            //Log.d(TAG, "Perda de conexão... Reconectando...");
            Toast.makeText(MainActivity.this, "Perda de conexão... Reconectando...", Toast.LENGTH_SHORT).show();
            connectMQTT();
            assinou = false;
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String msg = new String(message.getPayload());

            if (topic.equals("/Umidade")) { // Apresentada graficamente
                float fUmidade = Float.parseFloat(msg);
                pB.setProgress((int)fUmidade);
                uText.setText(msg);
            } else if (topic.equals("/NivelAgua")) {
                if (msg.equals("Alto"))
                    iV.setImageResource(R.drawable.caixacheia);
                else
                    iV.setImageResource(R.drawable.caixavazia);
            } else if (topic.equals("/Solenoide")){
                txtTeste2.setText(msg);
            }else {
                // The message was published
                int mId = 99;
                mBuilder.setSmallIcon(R.drawable.caixacheia)
                        .setContentTitle("Mensagem recebida")
                        .setContentText(topic + ": " + message.toString())
                        .setVibrate(new long[]{150, 300, 150, 600}) // Para Vibrar
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                // Para apitar
                // Executa a notificação
                mNotificationManager.notify(mId, mBuilder.build());
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            //Log.d(TAG, "Entregue!");
            Toast.makeText(MainActivity.this, "Entregue!", Toast.LENGTH_SHORT).show();
        }
    };

    private IMqttActionListener MqttCallBackApp = new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
            //Log.d(TAG, "onSuccess");
            Toast.makeText(MainActivity.this, "onSuccess", Toast.LENGTH_SHORT).show();
            if (!assinou) {
                subscribeMQTT();
                assinou = true;
            }
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken,
                              Throwable exception) {
            // The subscription could not be performed, maybe the user was not
            // authorized to subscribe on the specified topic e.g. using wildcards

            Toast.makeText(MainActivity.this, "onFailure", Toast.LENGTH_SHORT).show();
        }
    };

    // Método de inicialização da Activity
    protected void onCreate(Bundle savedInstanceState) {
        // Inicializa interfaces
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        UMaxima = findViewById(R.id.editTextMax);
        UMinima = findViewById(R.id.editTextMax);
        pB = findViewById(R.id.progressBar);
        iV = findViewById(R.id.imageView);
        uText = findViewById(R.id.textViewUm);
        txtTeste = findViewById(R.id.textView6);
        txtTeste2 = findViewById(R.id.textView7);

        initMQTT();
        connectMQTT();
        startNotifications();
    }

    // Método de incialização do cliente MQTT
    private void initMQTT() {
        client = new MqttAndroidClient(this.getApplicationContext(),
                "tcp://192.168.0.25:1883", clientId);
        client.setCallback(ClientCallBack);
    }

    // Inicialização do MQTT e conexão inicial
    private void connectMQTT() {
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("tht");
            options.setPassword("123".toCharArray());
            IMqttToken token = client.connect(options);
            token.setActionCallback(MqttCallBackApp);

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    // Cria as classes necessárias para notificações: Intent, PendingIntent e acessa o mNotificationManager
    private void startNotifications() {
        Intent resultIntent = new Intent(this, MainActivity.class);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

    }

    // Assina as mensagens MQTT desejadas
    public void subscribeMQTT() {
        int qos = 1;
        try {
            if (!client.isConnected()) {
                connectMQTT();
            }
            IMqttToken subTokenU = client.subscribe("/Umidade", qos);
            subTokenU.setActionCallback(MqttCallBackApp);
            IMqttToken subTokenB = client.subscribe("/Bomba", qos);
            subTokenB.setActionCallback(MqttCallBackApp);
            IMqttToken subTokenS = client.subscribe("/Solenoide", qos);
            subTokenS.setActionCallback(MqttCallBackApp);
            IMqttToken subTokenN = client.subscribe("/NivelAgua", qos);
            subTokenN.setActionCallback(MqttCallBackApp);

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    // Trata o clique do botão, publicando as mensagens
    public void onClickPub(View v) { // Evento disparado no Clique do botão
        // String topic = "test";
        // String message = "L1";
        try {
            if (!client.isConnected()) {
                connectMQTT();
            }
            client.publish("/UmidadeMaxima", new MqttMessage(UMaxima.getText().toString().getBytes("UTF-8")));
            client.publish("/UmidadeMinima", new MqttMessage(UMinima.getText().toString().getBytes("UTF-8")));
            //client.publish(topic, message.getBytes(), 0, false);
        } catch (MqttException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}
