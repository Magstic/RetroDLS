package mobilebae;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/** Chunked PCM stream renderer over a parsed MIDI song. */
public final class PcmStream extends SynthesisSupport {
    final PreviewRenderer renderer;
    final MidiSong song;
    final int totalFrames;
    final int songEndFrame;
    final int blockFrames;
    final int[] mixBlock;
    final int[] reverbBlock;
    final int[] chorusBlock;
    final short[] pcmBlock;
    final ChorusEffect chorus;
    final ReverbEffect reverb;
    final EffectGate chorusGate;
    final EffectGate reverbGate;
    int eventIndex;
    int renderFrame;
    int readFrame;
    int blockReadFrame;
    int blockFrameCount;

    PcmStream(PreviewRenderer renderer, MidiSong song) {
        this.renderer = renderer;
        this.song = song;
        totalFrames = (int) Math.min(renderer.maxSamples,
                Math.max(1, song.lengthMicros * renderer.sampleRate / 1000000L + renderer.sampleRate));
        songEndFrame = (int) Math.min(totalFrames,
                Math.max(0L, song.lengthMicros * renderer.sampleRate / 1000000L));
        blockFrames = renderer.blockFrames;
        mixBlock = new int[blockFrames * 2];
        reverbBlock = new int[blockFrames];
        chorusBlock = new int[blockFrames];
        pcmBlock = new short[blockFrames * 2];
        chorus = new ChorusEffect(renderer.sampleRate);
        reverb = new ReverbEffect(renderer.sampleRate);
        chorusGate = new EffectGate(chorus.tailFrames(), blockFrames);
        reverbGate = new EffectGate(reverb.tailFrames(), blockFrames);
        renderer.childTailGainQ16 = childTailGainQ16(PreviewRenderer.childTailInput(song));
        renderer.reverbBus = reverbBlock;
        renderer.chorusBus = chorusBlock;
        int[] childPrime = new int[blockFrames * 2];
        renderer.childDynamics.process(childPrime, 0, childPrime, 0, 2, blockFrames, false);
    }

    public int blockFrames() {
        return blockFrames;
    }

    public int totalFrames() {
        return totalFrames;
    }

    public int positionFrames() {
        return readFrame;
    }

    public boolean finished() {
        return readFrame >= totalFrames;
    }

    public int read(short[] stereoPcm, int offsetFrames, int frames) {
        if (stereoPcm == null) {
            throw new NullPointerException("stereoPcm");
        }
        int capacityFrames = stereoPcm.length / 2;
        if (offsetFrames < 0 || frames < 0 || offsetFrames > capacityFrames - frames) {
            throw new IndexOutOfBoundsException("invalid frame range");
        }
        int copied = 0;
        while (copied < frames && readFrame < totalFrames) {
            if (blockReadFrame >= blockFrameCount) {
                blockFrameCount = renderNextBlock();
                blockReadFrame = 0;
                if (blockFrameCount == 0) {
                    break;
                }
            }
            int n = Math.min(frames - copied, blockFrameCount - blockReadFrame);
            System.arraycopy(pcmBlock, blockReadFrame * 2, stereoPcm, (offsetFrames + copied) * 2, n * 2);
            blockReadFrame += n;
            readFrame += n;
            copied += n;
        }
        return copied;
    }

    int renderNextBlock() {
        if (renderFrame >= totalFrames) {
            return 0;
        }
        int frames = Math.min(blockFrames, totalFrames - renderFrame);
        Arrays.fill(mixBlock, 0, frames * 2, 0);
        Arrays.fill(reverbBlock, 0, frames, 0);
        Arrays.fill(chorusBlock, 0, frames, 0);
        renderer.currentBlockReverbActive = false;
        renderer.currentBlockChorusActive = false;
        int local = 0;
        while (local < frames) {
            int absolute = renderFrame + local;
            while (eventIndex < song.events.size()) {
                MidiEvent event = song.events.get(eventIndex);
                int eventFrame = eventFrame(event);
                if (eventFrame > absolute) {
                    break;
                }
                renderer.handle(event);
                eventIndex++;
            }
            int target = renderFrame + frames;
            if (eventIndex < song.events.size()) {
                target = Math.min(target, eventFrame(song.events.get(eventIndex)));
            }
            if (target <= absolute) {
                continue;
            }
            renderer.mixUntil(mixBlock, local, target - renderFrame);
            local = target - renderFrame;
        }
        renderer.childDynamics.process(mixBlock, 0, mixBlock, 0, 2, frames, false);
        if (renderer.chorusEnabled && chorusGate.processThisBlock(renderer.currentBlockChorusActive)) {
            chorus.process(chorusBlock, mixBlock, 0, frames);
        }
        if (renderer.reverbEnabled && reverbGate.processThisBlock(renderer.currentBlockReverbActive)) {
            reverb.process(reverbBlock, mixBlock, 0, frames);
        }
        if (renderFrame < songEndFrame) {
            renderer.mixDynamics.process(mixBlock, 0, mixBlock, 0, 2, frames, false);
        }
        for (int i = 0; i < frames * 2; i++) {
            pcmBlock[i] = finalMixSample(mixBlock[i]);
        }
        renderFrame += frames;
        return frames;
    }

    int eventFrame(MidiEvent event) {
        return (int) Math.min(totalFrames, event.micros * renderer.sampleRate / 1000000L);
    }
}
