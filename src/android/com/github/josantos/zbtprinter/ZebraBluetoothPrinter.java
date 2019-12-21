package com.github.josantos.zbtprinter;

import java.io.IOException;
import android.os.Bundle;
import android.os.Looper;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import android.util.Log;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;
import com.zebra.sdk.comm.BluetoothConnection;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.comm.TcpConnection;
import com.zebra.sdk.printer.PrinterLanguage;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;

public class ZebraBluetoothPrinter extends CordovaPlugin {

    private static final String LOG_TAG = "ZebraBluetoothPrinter";
    private Connection printerConnection;
    private ZebraPrinter printer;
    //String mac = "AC:3F:A4:1D:7A:5C";

    public ZebraBluetoothPrinter() {
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (action.equals("print")) {
            try {
                String mac = args.getString(0);
                String msg = args.getString(1);
                sendData(callbackContext, mac, msg);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        if (action.equals("find")) {
            try {
                findPrinter(callbackContext);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }
    
    public void findPrinter(final CallbackContext callbackContext) {
      new Thread(new Runnable() {
            @Override
            public void run() {
              try {
                  BluetoothDiscoverer.findPrinters(cordova.getActivity().getApplicationContext(), new DiscoveryHandler() {

                      public void foundPrinter(DiscoveredPrinter printer) {
                          String macAddress = printer.address;
                          //I found a printer! I can use the properties of a Discovered printer (address) to make a Bluetooth Connection
                          callbackContext.success(printer.address);
                      }

                      public void discoveryFinished() {
                          //Discovery is done
                      }

                      public void discoveryError(String message) {
                          //Error during discovery
                          callbackContext.error(message);
                      }
                  });
              } catch (Exception e) {
                  callbackContext.error(e.getMessage());
              }
            }
        }).start();    
    }

    /*
     * This will send data to be printed by the bluetooth printer
     */
    void sendData(final CallbackContext callbackContext, final String mac, final String msg) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
              printer = connect(mac);
              if (printer != null) {
                  sendLabel(msg);
                  callbackContext.success(msg);
              } else {
                  disconnect();
                  callbackContext.error("Error");
              }
          }
        }).start();
    }

    public ZebraPrinter connect(String mac) {
        printerConnection = null;
        if (isBluetoothSelected()) {
            printerConnection = new BluetoothConnection(mac);
        } 

        try {
            printerConnection.open();
        } catch (ConnectionException e) {
            sleep(1000);
            disconnect();
        }

        ZebraPrinter printer = null;

        if (printerConnection.isConnected()) {
            try {
                printer = ZebraPrinterFactory.getInstance(printerConnection);
                PrinterLanguage pl = printer.getPrinterControlLanguage();
            } catch (ConnectionException e) {
                printer = null;
                sleep(1000);
                disconnect();
            } catch (ZebraPrinterLanguageUnknownException e) {
                printer = null;
                sleep(1000);
                disconnect();
            }
        }

        return printer;
    }
    public Boolean isBluetoothSelected(){
      return true;
    }
    public void disconnect() {
        try {
            if (printerConnection != null) {
                printerConnection.close();
            }
        } catch (ConnectionException e) {
        } finally {
        }
    }
    private void sendLabel(String msg) {
        try {
            byte[] configLabel = getConfigLabel(msg);
            printerConnection.write(configLabel);
            sleep(1500);
        } catch (ConnectionException e) {

        } finally {
            disconnect();
        }
    }

    private byte[] getConfigLabel(String msg) {
        PrinterLanguage printerLanguage = printer.getPrinterControlLanguage();

        byte[] configLabel = null;
        if (printerLanguage == PrinterLanguage.ZPL) {
            configLabel = msg.getBytes();
        } else if (printerLanguage == PrinterLanguage.CPCL) {
            String cpclConfigLabel = msg.getBytes();
            configLabel = cpclConfigLabel.getBytes();
        }
        return configLabel;
    }
    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
