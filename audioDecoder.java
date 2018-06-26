import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class AudioCodec {
    private File file;
    private MediaExtractor mediaExtractor;
    private MediaCodec mediaDecoder;
    private MediaFormat mediaFormat;

    public AudioCodec(File file) {
        this.file = file;
        try {
            mediaExtractor = new MediaExtractor();
            mediaExtractor.setDataSource(file.getAbsolutePath());
        } catch (Exception err) {
            err.printStackTrace();
        }
        int numTracks = mediaExtractor.getTrackCount();
        for (int i=0; i<numTracks; i++) {
            mediaFormat = mediaExtractor.getTrackFormat(i);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                mediaExtractor.selectTrack(i);
                try {
                    mediaDecoder = MediaCodec.createDecoderByType(mime);
                    mediaDecoder.configure(mediaFormat, null, null, 0);
                    break;
                } catch (Exception err) {
                    err.printStackTrace();
                }
            }
        }
        if (mediaDecoder == null)
            throw new IllegalArgumentException("Invalid file format");
    }

    public int getSampleRate() {
        return mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    }

    public byte[] getRawByteData() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        return decodeWithCodec(info).toByteArray();
    }

    public float[] getRawFloatData() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        byte[] bytes = decodeWithCodec(info).toByteArray();

        // first to short for 16 bit type
        ShortBuffer shortBuffer = ByteBuffer.wrap(bytes).asShortBuffer();
        short[] rawShortData = new short[shortBuffer.capacity()];
        shortBuffer.get(rawShortData);

        float[] floatData = new float[rawShortData.length];
        for (int i = 0; i < rawShortData.length; i++)
            floatData[i] = (float) rawShortData[i] / 0x8000;    // -1 to 1 floats
        return floatData;
    }

    public void saveBytesAsFile(byte[] bytes, String filename) {
        File newFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/" + filename + ".mp3");
        try {
            FileOutputStream fos = new FileOutputStream(newFile);
            fos.write(bytes);
            fos.flush();
            fos.close();
        } catch (Exception err) { err.printStackTrace(); }
    }

    private byte[] getDirectBytes() {
        byte bytes[] = new byte[ (int) file.length()];
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            DataInputStream dis = new DataInputStream(bis);
            dis.readFully(bytes);
        }
        catch (Exception err) { err.printStackTrace(); }
        return bytes;
    }

    private ByteArrayOutputStream decodeWithCodec(MediaCodec.BufferInfo info) {
        if (mediaDecoder == null)
            return null;
        mediaDecoder.start();
        int inputBufferIndex;
        int outputBufferIndex;
        ByteBuffer outputBuffer;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int sampleSize = 0;
        while (sampleSize != -1) {
            // while there are more samples to be read, read samples from mediaExtractor into mediaCodec
            inputBufferIndex = mediaDecoder.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0)
                sampleSize = mediaExtractor.readSampleData(mediaDecoder.getInputBuffer(inputBufferIndex), 0);
            if (sampleSize != -1) {
                mediaDecoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, mediaExtractor.getSampleTime(), 0);
                mediaExtractor.advance();
            }
            else mediaDecoder.queueInputBuffer(inputBufferIndex, 0, 0,0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

            // output data from mediaCodec
            outputBufferIndex = mediaDecoder.dequeueOutputBuffer(info, 10000);
            if (outputBufferIndex >= 0) {
                // end of file
                if (info.flags != 0) {
                    mediaDecoder.stop();
                    mediaDecoder.release();
                    mediaDecoder = null;
                    return null;
                }
                outputBuffer = mediaDecoder.getOutputBuffer(outputBufferIndex);
                byte[] decodedData = new byte[info.size - info.offset];
                outputBuffer.get(decodedData);
                for (byte b : decodedData)
                    outputStream.write(b);
                outputBuffer.clear();
                mediaDecoder.releaseOutputBuffer(outputBufferIndex, false);
            }
        }
        return outputStream;
    }
}