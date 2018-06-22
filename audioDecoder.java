import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class AudioFileDecoder {
    private MediaExtractor mediaExtractor = new MediaExtractor();
    private MediaCodec mediaCodec;
    private MediaFormat mediaFormat;

    public AudioFileDecoder(String filename) {
        try {
            mediaExtractor.setDataSource(filename);
        } catch (Exception err) {
            err.printStackTrace();
        }
        int numTracks = mediaExtractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat fileFormat = mediaExtractor.getTrackFormat(i);
            String mime = fileFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                mediaExtractor.selectTrack(i);
                try {
                    mediaCodec = MediaCodec.createDecoderByType(mime);
                    mediaCodec.configure(fileFormat, null, null, 0);
                    mediaFormat = fileFormat;
                    break;
                } catch (Exception err) {
                    err.printStackTrace();
                }
            }
        }
        if (mediaCodec == null)
            throw new IllegalArgumentException("Invalid file format");
    }

    public float[] getRawFloatData() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteArrayOutputStream stream = decode(info);
        byte[] bytes = stream.toByteArray();
        FloatBuffer floatBuffer = ByteBuffer.wrap(bytes).asFloatBuffer();
        float[] rawData = new float[floatBuffer.capacity()];
        floatBuffer.get(rawData);
        return rawData;
    }

    private ByteArrayOutputStream decode(MediaCodec.BufferInfo info) {
        if (mediaCodec == null)
            return null;
        mediaCodec.start();	
        int inputBufferIndex;
        int outputBufferIndex = 0;
        ByteBuffer outputBuffer;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int sampleSize = 0;
        while (sampleSize != -1) {
            // while there are more samples to be read, read samples from mediaExtractor into mediaCodec
            inputBufferIndex = mediaCodec.dequeueInputBuffer(30000);
            if (inputBufferIndex >= 0)
                sampleSize = mediaExtractor.readSampleData(mediaCodec.getInputBuffer(inputBufferIndex), 0);
            if (sampleSize != -1) {
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, sampleSize, mediaExtractor.getSampleTime(), 0);
                mediaExtractor.advance();
            }
            else mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

            // output data from mediaCodec
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 30000);
            if (outputBufferIndex >= 0) {
                // end of file
                if (info.flags != 0) {
                    mediaCodec.stop();
                    mediaCodec.release();
                    mediaCodec = null;
                    return null;
                }
                outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                byte[] decodedData = new byte[outputBuffer.remaining()];
                outputBuffer.get(decodedData);
                outputBuffer.clear();
                for (byte b : decodedData)
                    outputStream.write(b);
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
        }
        return outputStream;
    }
}