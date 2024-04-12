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
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptedDataSourceFactory implements  DataSource.Factory{
    private byte[] key;
    private byte[] iv;
    EncryptedDataSourceFactory(byte[] key, byte[]iv){
        this.key = key;
        this.iv = iv;
    }
    @NonNull
    @Override
    public DataSource createDataSource() {
        return new EncryptedDataSource(this);
    }

    private static class EncryptedDataSource implements DataSource {
        EncryptedDataSourceFactory factory;

        private long offset;
        private static final String TAG = "EncryptedDataSource";
        private TransferListener listener;
        private HttpURLConnection connection;
        private CipherInputStream inputStream;
        private DataSpec dataSpec;
        private Cipher cipher;

        EncryptedDataSource(EncryptedDataSourceFactory encryptedDataSourceFactory){
            this.factory = encryptedDataSourceFactory;
        }

        @Override
        public void addTransferListener(@NonNull TransferListener transferListener) {
            this.listener = transferListener;
        }

        @Override
        public long open(@NonNull DataSpec dataSpec) throws IOException {
            try {
                this.dataSpec = dataSpec;
                // Abrir una conexión HTTP con la URL del DataSpec
                URL url = new URL(dataSpec.uri.toString());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                long start = dataSpec.position - (dataSpec.position % 16);
                long end = dataSpec.length + (16 - dataSpec.length % 16);
                String range = "bytes=" + start + "-";
                if (dataSpec.length > 0){
                    range += end;
                }
                connection.addRequestProperty("Range", range);
                connection.connect();


                //Modificamos el IV basado en el bloque desde que empezara a leer.
                long blockOffset = start / 16;
                byte[] ivAdjusted = Arrays.copyOf(factory.iv, factory.iv.length);
                ByteBuffer byteBuffer = ByteBuffer.wrap(ivAdjusted);
                byteBuffer.position(8);
                long counter = byteBuffer.getLong() + blockOffset;
                byteBuffer.position(8);
                byteBuffer.putLong(counter);

                cipher = Cipher.getInstance("AES/CTR/NoPadding");
                IvParameterSpec ivParameterSpec = new IvParameterSpec(ivAdjusted);
                Key secretKey = new SecretKeySpec(factory.key, "AES");
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);

                // Obtener el InputStream del archivo remoto
                inputStream = new CipherInputStream(connection.getInputStream(), cipher);
                if (start < dataSpec.position){
                    byte[]drain = new byte[(int)(dataSpec.position-start)];
                    int totalRead = 0;
                    while (totalRead < drain.length){
                        totalRead += inputStream.read(drain, totalRead, drain.length - totalRead);
                    }
                }
                // Notificar al listener sobre el inicio de la transferencia
                if (listener != null) {
                    listener.onTransferStart(this, dataSpec, true);
                }

                return connection.getContentLengthLong(); // Devolver la longitud del contenido
            } catch (IOException e) {
                if (listener != null) {
                    //listener.onTransferEnd();
                    listener.onTransferEnd(this, dataSpec, true);
                }
                throw e;
            }
            catch (Exception e){
                throw new IOException();
            }
        }



        @Override
        public Uri getUri() {
            return dataSpec!=null?dataSpec.uri: null;
        }



        @Override
        public void close() throws IOException {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } finally {
                    inputStream = null;
                    if (connection != null) {
                        connection.disconnect();
                        connection = null;
                    }
                }
            }
        }

        @Override
        public int read(@NonNull byte[] buffer, int offset, int readLength) throws IOException {
            if (inputStream == null) {
                throw new IOException("Input stream is not opened");
            }
            try{
                int bytesRead = inputStream.read(buffer, offset, readLength);
                return bytesRead;
            }
            catch (IOException e){
                e.printStackTrace();
                return -1;
            }
        }
    }

}
