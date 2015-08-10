package org.dcm4chee.storage.test.unit.filesystem;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Created by player on 10-Aug-15.
 */
@Ignore
public class NIOPerfTest {

    //@Test
    public void test() throws IOException {
        OutputStream outputStream = Files.newOutputStream(Paths.get("target/file1.txt"), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        writeLittleChunks(outputStream);
    }

    //@Test
    public void testBulk() throws IOException {
        OutputStream outputStream = Files.newOutputStream(Paths.get("target/file1.txt"), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        writeBigChunks(outputStream);
    }


    @Test
    public void testAsync() throws IOException {
        OutputStream outputStream = Files.newOutputStream(Paths.get("target/file1.txt"), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

//        outputStream = new BufferedOutputStream(outputStream, 65536);

        writeLittleChunks(outputStream);


    }

    private void writeLittleChunks(OutputStream outputStream) throws IOException {
        byte[] bytes = new byte[128];
        for (byte i=0;i>=0;i++)
            bytes[i] = i;

        // check chunked
        for (int i =0;i<4194304;i++)
            outputStream.write(bytes);
    }

    private void writeBigChunks(OutputStream outputStream) throws IOException {
        byte[] bytes = new byte[65536];
        for (int i=0;i<65536;i++)
            bytes[i] = (byte) i ;

        // check chunked
        for (int i =0;i<8192;i++)
            outputStream.write(bytes);
    }

}
