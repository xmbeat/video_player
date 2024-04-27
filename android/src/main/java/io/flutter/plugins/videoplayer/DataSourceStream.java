
package io.flutter.plugins.videoplayer;

import com.google.android.exoplayer2.upstream.DataSource;

import java.io.IOException;
import java.io.InputStream;

public class DataSourceStream extends InputStream {

    private final DataSource dataSource;
    byte[] buffer = new byte[4096];
    int offset = 0;
    int read = 0;
    public DataSourceStream(DataSource dataSource){
        this.dataSource = dataSource;
    }

    @Override
    public void close() throws IOException {
        dataSource.close();
    }

    @Override
    public int read() throws IOException {
       if (offset >= read){
           read = dataSource.read(buffer, 0, 1024);
           offset = 0;
           if (read == -1){
               return -1;
           }
       }
       return buffer[offset++] & 0xFF;
    }

}
