package battery.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

import butterknife.ButterKnife;
import butterknife.InjectView;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.android.view.ViewObservable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;


public class MainActivity extends ActionBarActivity {

    private static final String TAG = "Main";

    private static final int NEW_LINE_ASCII_VALUE = 10;

    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mmDevice;
    PublishSubject<String> stopper = PublishSubject.create();

    private PublishSubject<String> sender = PublishSubject.create();

    @InjectView(R.id.red)
    Button buttonRed;
    @InjectView(R.id.green)
    Button buttonGreen;
    @InjectView(R.id.blue)
    Button buttonBlue;

    @InjectView(R.id.open)
    Button openButton;
    @InjectView(R.id.send)
    Button sendButton;
    @InjectView(R.id.close)
    Button closeButton;

    @InjectView(R.id.label)
    TextView myLabel;
    @InjectView(R.id.entry)
    EditText myTextbox;

    boolean enableRed = false;

    boolean enableBlue = false;

    boolean enableGreen = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.inject(this);

        buttonRed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableRed = !enableRed;
                updateLeds();
            }
        });

        buttonGreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableGreen = !enableGreen;
                updateLeds();
            }
        });

        buttonBlue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableBlue = !enableBlue;
                updateLeds();
            }
        });

        //Open Button
        openButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    findBT();

                    if (mmDevice == null) {
                        toast("No device found");
                    } else {
                        openBT();
                    }
                } catch (IOException ex) {
                }
            }
        });

        //Send Button
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    sendData();
                } catch (IOException ex) {
                }
            }
        });
    }

    void findBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            myLabel.setText("No bluetooth adapter available");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().startsWith("HC")) {
                    mmDevice = device;
                    break;
                }
            }
        }
        myLabel.setText("Bluetooth Device Found");
    }

    @SuppressWarnings("unchecked")
    void openBT() throws IOException {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        BluetoothSocket socket = mmDevice.createRfcommSocketToServiceRecord(uuid);

        Subscription senderSubscription = sender.subscribe(sendMessageToSocket(socket));

        Observable.create(byListeningFromSocket(socket))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .takeUntil(ViewObservable.clicks(closeButton))
                .forEach(handleArdiunoMessage(),
                        logError(),
                        toastAndCloseConnection(socket, senderSubscription));

        myLabel.setText("Bluetooth Opened");
    }

    private Action1<String> sendMessageToSocket(final BluetoothSocket socket) {
        return new Action1<String>() {
            @Override
            public void call(String s) {
                try {
                    socket.getOutputStream().write(s.getBytes());
                } catch (IOException e) {
                    Log.e(TAG, "Unable to send data to socket", e);
                }
            }
        };
    }

    private Action1<String> handleArdiunoMessage() {
        return new Action1<String>() {
            @Override
            public void call(String s) {
                myLabel.setText(s);

                char red = s.charAt(0);
                char green = s.charAt(1);
                char blue = s.charAt(2);

                if (red == '1') {
                    buttonRed.setBackgroundColor(Color.YELLOW);
                } else {
                    buttonRed.setBackgroundColor(Color.BLACK);
                }

                if (green == '1') {
                    buttonGreen.setBackgroundColor(Color.RED);
                } else {
                    buttonGreen.setBackgroundColor(Color.BLACK);
                }

                if (blue == '1') {
                    buttonBlue.setBackgroundColor(Color.BLUE);
                } else {
                    buttonBlue.setBackgroundColor(Color.BLACK);
                }
            }
        };
    }

    private Action1<Throwable> logError() {
        return new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                Log.e(TAG, "Error when reading bluetooth", throwable);
            }
        };
    }

    private Action0 toastAndCloseConnection(final BluetoothSocket socket, final Subscription subscription) {
        return new Action0() {
            @Override
            public void call() {
                try {
                    subscription.unsubscribe();
                    socket.getOutputStream().close();
                    socket.getInputStream().close();
                    socket.close();
                    myLabel.setText("Bluetooth Closed");
                } catch (IOException ex) {
                    Log.e(TAG, "Unable to shutdown the connections", ex);
                }
            }
        };
    }

    private Observable.OnSubscribe<String> byListeningFromSocket(final BluetoothSocket socket) {
        return new Observable.OnSubscribe<String>() {
            @Override
            public void call(final Subscriber<? super String> subscriber) {
                try {
                    InputStream socketInputStream = socket.getInputStream();

                    byte[] readBuffer = new byte[1024];
                    int readBufferPosition = 0;

                    while (!subscriber.isUnsubscribed()) {
                        int bytesAvailable = socketInputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            socketInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == NEW_LINE_ASCII_VALUE) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;
                                    subscriber.onNext(data);
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                } catch (IOException ex) {
                    subscriber.onError(ex);
                    return;
                }

                subscriber.onCompleted();
            }
        };
    }

    void sendData() throws IOException {
        String msg = myTextbox.getText().toString();
        msg += "\n";
        sender.onNext(msg);
    }

    void updateLeds() {
        String red = enableRed ? "1" : "0";
        String blue = enableBlue ? "1" : "0";
        String green = enableGreen ? "1" : "0";

        String message = red + "," + green + "," + blue + "\n";

        Log.i(TAG, "Sending " + message + " over bluetoth");

        sender.onNext(message);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}