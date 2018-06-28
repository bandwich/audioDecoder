import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
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

    public int getChannelCount() {
        return mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    }

    public byte[] getRawBytes() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        return decodeWithCodec(info).toByteArray();
    }

    // takes channel and returns samples in that channel as float
    public float[] getSamplesAsFloat(int channel) {
        if (channel > mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) || channel < 0)
            throw new IllegalArgumentException("Channel out of bounds");

        int numChannels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        byte[] bytes = decodeWithCodec(info).toByteArray();

        // first convert to short for 16 bit ints ( byte order not modified ), all channels still interleaved
        ShortBuffer samples = ByteBuffer.wrap(bytes).asShortBuffer();
        short[] rawShortSamples = new short[samples.remaining()];
        samples.get(rawShortSamples);

        // return samples in specified channel as float
        float[] floatSamples = new float[rawShortSamples.length / numChannels];
        for (int i = 0; i < floatSamples.length; i++)
            floatSamples[i] = (float) rawShortSamples[i * numChannels + channel] / 0x8000;    // floats in range -1 to 1
        return floatSamples;
    }

    // assumes bytes are already encoded
    // saves file to public music directory
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

    // decoder returns raw PCM data
    // if stereo, samples are interleaved
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
                outputBuffer.position(info.offset);
                outputBuffer.limit(info.size + info.offset);
                byte[] decodedData = new byte[info.size - info.offset];
                outputBuffer.get(decodedData);
                for (byte b : decodedData)
                    outputStream.write(b);
                outputBuffer.clear();
                mediaDecoder.releaseOutputBuffer(outputBufferIndex, false);
            }
        }
        mediaDecoder.stop();
        return outputStream;
    }
}