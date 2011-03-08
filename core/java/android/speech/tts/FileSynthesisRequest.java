/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package android.speech.tts;

import android.media.AudioFormat;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Speech synthesis request that writes the audio to a WAV file.
 */
class FileSynthesisRequest extends SynthesisRequest {

    private static final String TAG = "FileSynthesisRequest";
    private static final boolean DBG = false;

    private static final int WAV_HEADER_LENGTH = 44;
    private static final short WAV_FORMAT_PCM = 0x0001;

    private final Object mStateLock = new Object();
    private final File mFileName;
    private int mSampleRateInHz;
    private int mAudioFormat;
    private int mChannelCount;
    private RandomAccessFile mFile;
    private boolean mStopped = false;

    FileSynthesisRequest(String text, File fileName) {
        super(text);
        mFileName = fileName;
    }

    @Override
    void stop() {
        synchronized (mStateLock) {
            mStopped = true;
            cleanUp();
        }
    }

    /**
     * Must be called while holding the monitor on {@link #mStateLock}.
     */
    private void cleanUp() {
        closeFile();
        if (mFile != null) {
            mFileName.delete();
        }
    }

    /**
     * Must be called while holding the monitor on {@link #mStateLock}.
     */
    private void closeFile() {
        try {
            if (mFile != null) {
                mFile.close();
                mFile = null;
            }
        } catch (IOException ex) {
            Log.e(TAG, "Failed to close " + mFileName + ": " + ex);
        }
    }

    @Override
    public int start(int sampleRateInHz, int audioFormat, int channelCount) {
        if (DBG) {
            Log.d(TAG, "FileSynthesisRequest.start(" + sampleRateInHz + "," + audioFormat
                    + "," + channelCount + ")");
        }
        synchronized (mStateLock) {
            if (mStopped) {
                if (DBG) Log.d(TAG, "Request has been aborted.");
                return TextToSpeech.ERROR;
            }
            if (mFile != null) {
                cleanUp();
                throw new IllegalArgumentException("FileSynthesisRequest.start() called twice");
            }
            mSampleRateInHz = sampleRateInHz;
            mAudioFormat = audioFormat;
            mChannelCount = channelCount;
            try {
                mFile = new RandomAccessFile(mFileName, "rw");
                // Reserve space for WAV header
                mFile.write(new byte[WAV_HEADER_LENGTH]);
                return TextToSpeech.SUCCESS;
            } catch (IOException ex) {
                Log.e(TAG, "Failed to open " + mFileName + ": " + ex);
                cleanUp();
                return TextToSpeech.ERROR;
            }
        }
    }

    @Override
    public int audioAvailable(byte[] buffer, int offset, int length) {
        if (DBG) {
            Log.d(TAG, "FileSynthesisRequest.audioAvailable(" + buffer + "," + offset
                    + "," + length + ")");
        }
        synchronized (mStateLock) {
            if (mStopped) {
                if (DBG) Log.d(TAG, "Request has been aborted.");
                return TextToSpeech.ERROR;
            }
            if (mFile == null) {
                Log.e(TAG, "File not open");
                return TextToSpeech.ERROR;
            }
            try {
                mFile.write(buffer, offset, length);
                return TextToSpeech.SUCCESS;
            } catch (IOException ex) {
                Log.e(TAG, "Failed to write to " + mFileName + ": " + ex);
                cleanUp();
                return TextToSpeech.ERROR;
            }
        }
    }

    @Override
    public int done() {
        if (DBG) Log.d(TAG, "FileSynthesisRequest.done()");
        synchronized (mStateLock) {
            if (mStopped) {
                if (DBG) Log.d(TAG, "Request has been aborted.");
                return TextToSpeech.ERROR;
            }
            if (mFile == null) {
                Log.e(TAG, "File not open");
                return TextToSpeech.ERROR;
            }
            try {
                // Write WAV header at start of file
                mFile.seek(0);
                int fileLen = (int) mFile.length();
                mFile.write(makeWavHeader(mSampleRateInHz, mAudioFormat, mChannelCount, fileLen));
                closeFile();
                return TextToSpeech.SUCCESS;
            } catch (IOException ex) {
                Log.e(TAG, "Failed to write to " + mFileName + ": " + ex);
                cleanUp();
                return TextToSpeech.ERROR;
            }
        }
    }

    private byte[] makeWavHeader(int sampleRateInHz, int audioFormat, int channelCount,
            int fileLength) {
        // TODO: is AudioFormat.ENCODING_DEFAULT always the same as ENCODING_PCM_16BIT?
        int sampleSizeInBytes = (audioFormat == AudioFormat.ENCODING_PCM_8BIT ? 1 : 2);
        int byteRate = sampleRateInHz * sampleSizeInBytes * channelCount;
        short blockAlign = (short) (sampleSizeInBytes * channelCount);
        short bitsPerSample = (short) (sampleSizeInBytes * 8);

        byte[] headerBuf = new byte[WAV_HEADER_LENGTH];
        ByteBuffer header = ByteBuffer.wrap(headerBuf);
        header.order(ByteOrder.LITTLE_ENDIAN);

        header.put(new byte[]{ 'R', 'I', 'F', 'F' });
        header.putInt(fileLength - 8);  // RIFF chunk size
        header.put(new byte[]{ 'W', 'A', 'V', 'E' });
        header.put(new byte[]{ 'f', 'm', 't', ' ' });
        header.putInt(16);  // size of fmt chunk
        header.putShort(WAV_FORMAT_PCM);
        header.putShort((short) channelCount);
        header.putInt(sampleRateInHz);
        header.putInt(byteRate);
        header.putShort(blockAlign);
        header.putShort(bitsPerSample);
        header.put(new byte[]{ 'd', 'a', 't', 'a' });
        int dataLength = fileLength - WAV_HEADER_LENGTH;
        header.putInt(dataLength);

        return headerBuf;
    }

}
