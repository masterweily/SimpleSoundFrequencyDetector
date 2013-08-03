package com.example.SimpleSoundFrequencyDetector;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;


public class MyActivity extends Activity
{
    /**
     * Called when the activity is first created.
     */

    private AudioIn audioIn;
    private boolean detectingSound = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        ((Button) findViewById(R.id.startBtn)).setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View view) {
                startDetectSound();
            }
        });
        ((Button) findViewById(R.id.stopBtn)).setOnClickListener(new Button.OnClickListener(){

            @Override
            public void onClick(View view) {
                stopDetectSound();
            }
        });
    }

    private void startDetectSound() {
        if (!detectingSound)
        {
            audioIn = new AudioIn();
            detectingSound = true;
        }

    }

    private void stopDetectSound() {
        if (detectingSound)
        {
            audioIn.close();
            detectingSound = false;
        }

    }

    private void showFrequency(int frequency) {
        ( (TextView) findViewById(R.id.fequencyView) ).setText( frequency + "Hz" );
    }

        private class AudioIn extends Thread {
            private boolean stopped    = false;

            private final int[] AVAILABLE_SAMPLE_RATES = {8000, 11025, 16000, 22050, 44100}; // In Hz

            private final int SAMPLE_RATE = AVAILABLE_SAMPLE_RATES[4];

            private AudioIn() {
                start();
            }

            @Override
            public void run() {
                Log.d("detect-sound","thread start");
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                AudioRecord recorder = null;
                short[][]   buffers;//  = new short[256][160];
                int         ix       = 0;


                try { // ... initialise

                    int N = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);

                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            N);

                    buffers = new short[256][N];

                    recorder.startRecording();

                    // ... loop

                    Baseline baseline = buildBaseline(recorder, buffers);

                    while(!stopped) {
                        short[] samples = buffers[ix++ % buffers.length];

                        Time time = new Time();
                        time.setToNow();
                        N = recorder.read(samples,0,samples.length);
                        samples = baseline.filter(time,samples);

                        process(samples);
                    }
                } catch(Throwable x) {
                    Log.w("Error","Error reading voice audio",x);
                } finally {
                    close(recorder);
                }
            }

            private void close(AudioRecord recorder) {
                recorder.stop();
            }

            private void process(short[] buffer) {
                final int frequency = ZeroCrossing.calculate(SAMPLE_RATE,buffer);
                final short amplitude = amplitude(buffer);

    //            Log.d("filtered", Arrays.toString(buffer));
                if (amplitude > 15000) {
                    Log.d("process-frequency", frequency + "Hz" );
                    Log.d("process-amplitude", amplitude +"" );
                    Log.d("process-samples", StringUtils.join(buffer, "\n"));
                    smartProcess(SAMPLE_RATE,buffer, amplitude);
                }


                if ( Math.random() > 0.9 ){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // TODO Auto-generated method stub

                            showFrequency(frequency);
                        }
                    });
                }



            }

            private void smartProcess(int sample_rate, short[] buffer, short amplitude) {
                int[] peaks = findPeaks(buffer);
                short[] means = calcMeans(peaks,buffer);
                Log.d("smartProcess-amplitude", "" + amplitude);
                Log.d("smartProcess-samples-" + amplitude, "\n" + StringUtils.join(buffer, "\n"));
                Log.d("smartProcess-peaks-" + amplitude, "\n" + StringUtils.join(peaks, "\n"));
                Log.d("smartProcess-means-" + amplitude, "\n" + StringUtils.join(means, "\n"));
            }

            private short[] calcMeans(int[] peaks, short[] buffer) {
                short[] means = new short[peaks.length-1];
                for( int i = 0 ; i < peaks.length-1 ; i ++ ) {
                    int peak = peaks[i];
                    int nextPeak = peaks[i+1];
                    int range = nextPeak - peak;
                    means[i] = (short) (sumArray(buffer,peak,nextPeak-1 ) / range);
                }
                return means;
            }

            private int sumArray(short[] array, int from, int to) {
                int sum = 0;
                for ( int i = from ; i <= to ; i ++ ) {
                    sum += array[i];
                }
                return sum;
            }


            private int[] findPeaks(short[] buffer) {
                int peaksCount = 0;
                boolean[] peakFlags = new boolean[buffer.length];
                // find picks
                peakFlags[0] = false;
                for ( int i = 1 ; i < buffer.length ; i++ ) {
                   if (isPeak(buffer, i)) {
                       peakFlags[i] = true;
                       peaksCount ++;
                    }
                    else {
                       peakFlags[i] = false;
                   }
                }
                // generate array of picks indexes and return
                int[] output = new int[peaksCount];
                int j = 0;
                for ( int i = 0 ; i <  peakFlags.length ; i ++ ) {
                    if (peakFlags[i]) {
                        output[j] = i;
                        j++;
                    }
                }
                return output;
            }

            private boolean isPeak(short[] buffer, int i) {
                try {
                    return (buffer[i-1] <= buffer[i] && buffer[i+1] <= buffer[i]);
                }
                catch (IndexOutOfBoundsException e) {
                    return false;
                }
            }


            private short amplitude(short[] buffer) {
                short max = 0;
                for (int i=0 ; i < buffer.length ; i ++) {
                    short abs = (short)Math.abs(buffer[i]);
                    if (abs > max)
                        max = abs;
                }
                return max;
            }

            private Baseline buildBaseline(AudioRecord recorder, short[][] buffers) {
                Baseline baseline = new Baseline(SAMPLE_RATE);
                for ( int i = 0 ; i < buffers.length / 10 ; i++ ) {
                    short[] buffer = buffers[i];
                    Time time = new Time();
                    time.setToNow();
                    int length = recorder.read(buffer,0,buffer.length);
                    baseline.write(time, buffer, length);
                }
                return baseline;
            }


            private void close() {
                stopped = true;
            }

        }

    public static class Wave
    {
        private final int sampleRate;
        private long offset;
        private short amplitude;
        private int frequency;

        public Wave(Time finishTime, short[] samples, int sampleRate, int length)
        {
            this.sampleRate = sampleRate;
            this.amplitude = findAmplitude(samples,length);
            this.frequency = findFrequency(samples);
            this.offset = findOffset(finishTime,samples,length);

            Log.d("wave", "\namp: " + this.amplitude + "\nfrequency: " + this.frequency + "Hz\noffset: " + this.offset + "\nfinshTime: " + finishTime.toMillis(false) );
        }

        private long findOffset(Time finishTime, short[] samples, int length) {
            int lastMaxIndex = findLastMaxIndex(samples,length);
            Log.d("wave-findOffset", lastMaxIndex + "");
//            Log.d("wave-findOffset", Arrays.toString(samples));
//            Log.d("wave-findOffset", StringUtils.join(samples, "\n"));
            Log.d("wave-findOffset", length + "");
            Log.d("wave-findOffset", length + "");
            return finishTime.toMillis(false) - 1000 * lastMaxIndex / sampleRate;
        }

        private static int findLastMaxIndex(short[] samples, int length) {
            int found = -1;
            for ( int i = length-2 ; i >= 0 ; i--) {
                short current = samples[i];
                short next = samples[i+1];
                short prev = samples[i-1];
                if (next < current && prev < current)
                    return i;// max found
            }
            return length-1; // case the max index not found, its the last index
        }

        private int findFrequency(short[] samples) {
            return ZeroCrossing.calculate(this.sampleRate,samples);
        }

        private short findAmplitude(short[] samples, int length) {
            short max = 0;
            for (int i=0 ; i < length ; i ++) {
                short abs = (short)Math.abs(samples[i]);
                if (abs > max)
                    max = abs;
            }
            return max;
        }

        public Wave interference(Wave other) {
            //TODO
            return this;
        }

//        public short[] filter()

        public short getAmplitude() {
           // TODO
            return 0;
        }

        public double getFrequency() {
            // TODO
            return 0;
        }

        public double getPhase() {
            // TODO
            return 0;
        }



    }

    public static class StringUtils
    {
        public static String join(short[] array, String seperator) {
            String joined = "";
            for ( int i = 0 ; i < array.length ; i ++ ) {
                joined += seperator + array[i];
            }
            return joined;
        }

        public static String join(int[] array, String seperator) {
            String joined = "";
            for ( int i = 0 ; i < array.length ; i ++ ) {
                joined += seperator + array[i];
            }
            return joined;
        }
    }

    public class Baseline
    {

        ArrayList<Wave> waves;
        private int sampleRate;

        public Baseline(int sampleRate) {
            this.sampleRate = sampleRate;
            waves = new ArrayList<Wave>();
        }

        public void write(Time stopSampleTime, short[] samples, int length) {
           waves.add(new Wave(stopSampleTime, samples, this.sampleRate, length));
        }

        public short[] filter(Time stopSampleTime, short[] samples) {

            // TODO
            return samples;
        }

        public short valueForTime(Time time){
            // TODO
            return 0;
        }


    }

    public static class ZeroCrossing
    {
        private static final String TAG = "ZeroCrossing.java";

        /**
         * calculate frequency using zero crossings
         */
        public static int calculate(int sampleRate, short [] audioData)
        {
            int numSamples = audioData.length;
            int numCrossing = 0;
            for (int p = 0; p < numSamples-1; p++)
            {
                if ((audioData[p] > 0 && audioData[p + 1] <= 0) ||
                        (audioData[p] < 0 && audioData[p + 1] >= 0))
                {
                    numCrossing++;
                }
            }

            float numSecondsRecorded = (float)numSamples/(float)sampleRate;
            float numCycles = numCrossing/2;
            float frequency = numCycles/numSecondsRecorded;

            return (int)frequency;
        }
    }

}
