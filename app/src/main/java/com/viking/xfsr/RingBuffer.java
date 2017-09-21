package com.viking.xfsr;

/**
 * Created by 80011674 on 2017-09-21.
 */

public class RingBuffer {
    byte[] mBuffer = null;
    int mSize = 0;
    int mInPosition = 0;
    int mOutPosition = 0;

    public RingBuffer(int size) {
        mSize = size;
        mBuffer = new byte[size];

    }

    public void reset() {
        int mInPosition = 0;
        int mOutPosition = 0;
    }

    public int load() {
        return mInPosition >= mOutPosition ? mInPosition - mOutPosition : mSize - (mOutPosition - mInPosition);
    }

    public int free() {
        return mSize - 1 - load();
    }

    public boolean isEmpty() {
        return mInPosition == mOutPosition;
    }

    public boolean isFull() {
        return 0 == free();
    }

    public int write(byte[] in, int count) {
        count = Math.min(count, free());
        int to_end = mSize - mInPosition;

        if (mInPosition > mOutPosition && to_end < count) {
            System.arraycopy(in, 0, mBuffer, mInPosition, to_end);
            System.arraycopy(in, to_end, mBuffer, 0, count - to_end);
            mInPosition = count - to_end;
        } else {
            System.arraycopy(in, 0, mBuffer, mInPosition, count);
            mInPosition = (mInPosition + count) % mSize;
        }

        return count;
    }

    public int read(byte[] out, int count) {
        count = Math.min(count, load());
        int to_end = mSize - mOutPosition;

        if (mOutPosition > mInPosition && to_end < count) {
            System.arraycopy(mBuffer, mOutPosition, out, 0, to_end);
            System.arraycopy(mBuffer, 0, out, to_end, count - to_end);
            mOutPosition = count - to_end;
        } else {
            System.arraycopy(mBuffer, mOutPosition, out, 0, count);
            mOutPosition = (mOutPosition + count) % mSize;
        }

        return count;
    }
}
