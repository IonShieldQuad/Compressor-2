package ionshield.compressor.utils;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class BitInputStream {
    private static final int START_INDEX = 0;
    private static final int END_INDEX = 8;
    
    private boolean skipHighestBit = true;
    
    private int index = END_INDEX;
    
    private byte buffer = 0;
    DataInputStream in;
    
    public BitInputStream(InputStream in) {
        this.in = new DataInputStream(in);
    }
    
    private void refillBuffer() throws IOException {
        if (index > START_INDEX) {
            buffer = in.readByte();
            index = START_INDEX;
        }
    }
    
    public boolean readBit() throws IOException {
        if (index >= END_INDEX) {
            refillBuffer();
        }
        
        if (index == START_INDEX && skipHighestBit) {
            index++;
        }
        
        byte mask = (byte) ((byte)0b1 << (7 - index));
    
        index++;
        return (buffer & mask) != 0;
    }
    
    public byte readByte() throws IOException {
        byte out = 0;
        for (int i = 0; i < 8; i++) {
            boolean bit = readBit();
            byte mask = (byte) ((byte)0b1 << (7 - i));
            out = (byte) ((out & ~mask) | (bit ? mask : 0));
        }
        return out;
    }
}
