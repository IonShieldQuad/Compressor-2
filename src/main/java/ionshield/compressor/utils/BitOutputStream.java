package ionshield.compressor.utils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class BitOutputStream {
    private static final int START_INDEX = 0;
    private static final int END_INDEX = 8;
    
    private boolean skipHighestBit = true;
    
    private int index = 0;
    
    private byte buffer = 0;
    DataOutputStream out;
    
    public BitOutputStream(OutputStream out) {
        this.out = new DataOutputStream(out);
    }
    
    private void emptyBuffer() throws IOException {
        if (index > START_INDEX) {
            out.write(buffer);
            buffer = 0;
            index = START_INDEX;
        }
    }
    
    public void writeBit(boolean bit) throws IOException {
        if (index >= END_INDEX) {
            emptyBuffer();
        }
        if (index == START_INDEX && skipHighestBit) {
            index++;
        }
        byte mask = (byte) ((byte)0b1 << (7 - index));
        buffer = (byte)((buffer & ~mask) | (bit ? mask : 0b0));
        index++;
    }
    
    public void writeByte(byte b) throws IOException {
        for (int i = 0; i < 8; i++) {
            byte mask = (byte) ((byte)0b1 << (7 - i));
            boolean bit = (b & mask) != 0;
            writeBit(bit);
        }
    }
    
    public void flush() throws IOException {
        emptyBuffer();
        out.flush();
    }
}
