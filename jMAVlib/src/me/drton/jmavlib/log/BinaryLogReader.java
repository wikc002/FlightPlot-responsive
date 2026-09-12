package me.drton.jmavlib.log;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public abstract class BinaryLogReader implements LogReader {
    protected ByteBuffer buffer;
    protected FileChannel channel = null;
    protected long channelPosition = 0;
    private static final int BUFFER_SIZE = 524288;

    public BinaryLogReader(String fileName) throws IOException {
        buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.flip();
        channel = new RandomAccessFile(fileName, "r").getChannel();
    }

    @Override
    public void close() throws IOException {
        if (channel != null) { channel.close(); channel = null; }
    }

    public int fillBuffer() throws IOException {
        buffer.compact();
        int n = channel.read(buffer);
        buffer.flip();
        if (n < 0) {
            throw new EOFException();
        }
        channelPosition += n;
        return n;
    }

    public void fillBuffer(int required) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Log operation cancelled");
        while (buffer.remaining() < required) {
            buffer.compact();
            int n = channel.read(buffer);
            buffer.flip();
            if (n < 0) {
                throw new EOFException();
            }
            channelPosition += n;
        }
    }

    protected long position() throws IOException {
        return channelPosition - buffer.remaining();
    }

    protected int position(long pos) throws IOException {
        buffer.clear();
        channel.position(pos);
        channelPosition = pos;
        int n = channel.read(buffer);
        buffer.flip();
        if (n < 0) {
            throw new EOFException();
        }
        channelPosition += n;
        return n;
    }
}
