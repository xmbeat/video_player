
package io.flutter.plugins.videoplayer;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.Key;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesDataSourceFactory implements DataSource.Factory {
    private final byte[] key;
    private final byte[] iv;
    private final DataSource dataSource;

    public AesDataSourceFactory(byte[] key, byte[] iv, DataSource dataSource) {
        this.key = key;
        this.iv = iv;
        this.dataSource = dataSource;
    }
    @NonNull
    @Override
    public DataSource createDataSource() {
        return new EncryptedDataSource(this);
    }

    private static class EncryptedDataSource implements DataSource {
        AesDataSourceFactory factory;
        private TransferListener listener;
        private InputStream inputStream;
        private DataSpec dataSpec;

        private long offset = 0;
        private long total = 0;
        EncryptedDataSource(AesDataSourceFactory factory) {
            this.factory = factory;
        }

        @Override
        public void addTransferListener(@NonNull TransferListener transferListener) {
            this.listener = transferListener;
        }

        @Override
        public long open(@NonNull DataSpec dataSpec) throws IOException {
            this.offset = dataSpec.position;
            this.dataSpec = dataSpec;
            DataSource dataSource = this.factory.dataSource;

            long start = dataSpec.position - (dataSpec.position % 16);
            long end = dataSpec.length == -1? -1: dataSpec.length + (16 - dataSpec.length % 16);
            long len = dataSource.open(new DataSpec(dataSpec.uri, start, end));
            long blockOffset = start / 16;
            byte []ivAdjusted = Arrays.copyOf(factory.iv, factory.iv.length);
            ByteBuffer byteBuffer = ByteBuffer.wrap(ivAdjusted);
            long counter = byteBuffer.getLong(8) + blockOffset;
            byteBuffer.putLong(8, counter);
            try {
                Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
                IvParameterSpec ivParameterSpec = new IvParameterSpec(ivAdjusted);
                Key secretKey = new SecretKeySpec(factory.key, "AES");
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
                inputStream = new CipherInputStream(new DataSourceStream(dataSource), cipher);
                if (start < dataSpec.position){
                    byte[]drain = new byte[(int) (dataSpec.position - start)];
                    int totalRead = 0;
                    while (totalRead < drain.length){
                        totalRead += inputStream.read(drain, totalRead, drain.length - totalRead);
                    }
                }

                if (listener != null){
                    listener.onTransferStart(this, dataSpec, true);
                }
            }
            catch (Exception e){
                throw new IOException();
            }
            this.total = len - (dataSpec.position % 16);
            // Log.d("AES DATA SOURCE OPEN", "POSITION: " + dataSpec.position + "\tREAL POS: " + start + "\tLEN: " + len + "\tLEN ADJ:" + (len-(dataSpec.position % 16)));
            return len - (dataSpec.position % 16);

        }


        @Override
        public Uri getUri() {
            return dataSpec != null ? dataSpec.uri : null;
        }


        @Override
        public void close() throws IOException {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } finally {
                    inputStream = null;
                }
            }
        }

        @Override
        public int read(@NonNull byte[] buffer, int offset, int readLength) throws IOException {
            if (inputStream == null) {
                throw new IOException("Input stream is not opened");
            }
            // Log.d("AES DATA SOURCE", "from: " + this.offset + "\ttotal: " + this.total + "\treading: " + readLength);
            int len = inputStream.read(buffer, offset, readLength);
            // Log.d("AES DATA SOURCE", "read: " + len);
            this.offset += len;
            return len;
        }
    }

}
