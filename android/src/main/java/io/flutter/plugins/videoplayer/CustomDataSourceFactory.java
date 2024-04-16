package io.flutter.plugins.videoplayer;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import android.os.Handler;
import android.os.Looper;


import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.Key;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

public class CustomDataSourceFactory implements  DataSource.Factory{
    private QueuingEventSink sink;
    private Map<String, Object> eventResponses;
    CustomDataSourceFactory(QueuingEventSink sink, Map<String, Object> eventResponses){
        this.sink = sink;
        this.eventResponses = eventResponses;
    }
    @NonNull
    @Override
    public DataSource createDataSource() {
        return new CustomDataSource(this);
    }

    private static class CustomDataSource implements DataSource {
        CustomDataSourceFactory factory;
        private static final String TAG = "CustomDataSource";
        private TransferListener listener;
        private DataSpec dataSpec;

        CustomDataSource(CustomDataSourceFactory customDataSourceFactory){
            this.factory = customDataSourceFactory;
        }

        @Override
        public void addTransferListener(@NonNull TransferListener transferListener) {
            this.listener = transferListener;
        }

        @Override
        public long open(@NonNull DataSpec dataSpec) throws IOException {
            try {
                this.dataSpec = dataSpec;
                Map<String, Object> response = sendEventAndWaitForResponse("dataSourceOpen", new HashMap<String, Object>(){{
                    put("position", dataSpec.position);
                    put("length", dataSpec.length);
                }});
                // Notificar al listener sobre el inicio de la transferencia
                if (listener != null) {
                    listener.onTransferStart(this, dataSpec, true);
                }
                long result = (long) response.get("length");
                return result; // Devolver la longitud del contenido
            } catch (IOException e) {
                if (listener != null) {
                    //listener.onTransferEnd();
                    listener.onTransferEnd(this, dataSpec, true);
                }
                throw e;
            }
        }



        @Override
        public Uri getUri() {
            return dataSpec!=null?dataSpec.uri: null;
        }



        @Override
        public void close() throws IOException {
            sendEventAndWaitForResponse("dataSourceClose", null);
        }

        @Override
        public int read(@NonNull byte[] buffer, int offset, int readLength) throws IOException {
            Map<String, Object> response = sendEventAndWaitForResponse("dataSourceRead", new HashMap<String, Object>(){{
                put("readLength", readLength);
            }});
            byte[] data = (byte[])response.get("data");
            System.arraycopy(data, 0, buffer, offset, data.length);
            return data.length;
        
        }

        private Map<String, Object> sendEventAndWaitForResponse(String eventName, Map<String, Object> eventDetail) throws IOException{
            Map<String, Object> event = new HashMap<String, Object>();
            event.put("event", eventName);
            event.put("detail", eventDetail);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run (){
                    factory.sink.success(event);
                }
            });
            while(!factory.eventResponses.containsKey(eventName)){
                Thread.yield();
            }
            Object rawResponse = factory.eventResponses.remove(eventName);
           
            if (!(rawResponse instanceof Map)){
                throw new RuntimeException("Response is not a valid Map");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) rawResponse;
            if (response.get("errorCode")!=null){
                throw new IOException();
            }
            return response;
        }
    }

}
