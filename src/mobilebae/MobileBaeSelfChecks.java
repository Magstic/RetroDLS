package mobilebae;

import static mobilebae.SynthesisSupport.*;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** Package-level regression checks kept out of the runtime facade. */
class MobileBaeSelfChecks {
public static void main(String[] args) throws Exception {
    int passed = 0;
    for (java.lang.reflect.Method method : MobileBaeSelfChecks.class.getDeclaredMethods()) {
        if (method.getName().endsWith("SelfCheck") && method.getParameterTypes().length == 0
                && method.getReturnType() == Boolean.TYPE) {
            if (!((Boolean) method.invoke(null)).booleanValue()) {
                throw new AssertionError(method.getName());
            }
            passed++;
        }
    }
    System.out.println("OK " + passed + " built-in self-checks passed");
}

static boolean sustainGateSelfCheck() {
    int blockFrames = defaultRenderBlockFrames(22050);
    ChannelState channel = new ChannelState(0);
    Wave wave = new Wave(0, 1, 1, 22050, 16, blockFrames + 2, -1,
            new short[blockFrames + 3], new SampleInfo());
    Voice voice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), new Articulation(), 60, 127, channel, 22050);
    channel.sustain = true;
    voice.noteOff();
    boolean heldBySustain = !voice.keyHeld && voice.envelope.stage != Envelope.RELEASE;
    channel.sustain = false;
    boolean delayedAfterPedalUp = voice.envelope.stage != Envelope.RELEASE;
    for (int i = 0; i < blockFrames; i++) {
        voice.next();
    }
    boolean stillDelayedInsideBlock = voice.envelope.stage != Envelope.RELEASE;
    voice.next();
    return heldBySustain && delayedAfterPedalUp && stillDelayedInsideBlock
            && voice.envelope.stage >= Envelope.RELEASE;
}

static boolean rpnPitchRangeSelfCheck() {
    DlsBank empty = new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>());
    PreviewRenderer renderer = new PreviewRenderer(empty, 22050, 1);
    ChannelState channel = renderer.channels[0];
    renderer.controller(channel, 101, 0);
    renderer.controller(channel, 100, 0);
    renderer.controller(channel, 6, 12);
    boolean msb = channel.rpnValues[0] == (12 << 7);
    renderer.controller(channel, 38, 64);
    boolean lsb = channel.rpnValues[0] == ((12 << 7) | 64);
    renderer.controller(channel, 97, 1);
    renderer.controller(channel, 96, 2);
    return msb && lsb && channel.rpnValues[0] == ((12 << 7) | 65);
}

static boolean nrpnDisablesRpnSelfCheck() {
    DlsBank empty = new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>());
    PreviewRenderer renderer = new PreviewRenderer(empty, 22050, 1);
    ChannelState channel = renderer.channels[0];
    renderer.controller(channel, 99, 1);
    renderer.controller(channel, 98, 0);
    renderer.controller(channel, 6, 5);
    renderer.controller(channel, 38, 7);
    return channel.selectorMode == 2 && channel.rpnValues[0] == 0x0100;
}

static boolean modelSnapshotSelfCheck() {
    List<Region> regions = new ArrayList<Region>();
    Region region = new Region(false, new Articulation());
    region.tableIndex = 0;
    regions.add(region);
    Instrument instrument = new Instrument(0, 0, "DLS ", new Articulation(), regions);
    regions.clear();

    List<Instrument> instruments = new ArrayList<Instrument>();
    instruments.add(instrument);
    List<Wave> waves = new ArrayList<Wave>();
    SampleInfo waveSample = new SampleInfo();
    waveSample.attenuation = -65536;
    waves.add(new Wave(0, 1, 1, 8000, 16, 1, -1, new short[]{0, 0}, waveSample));
    DlsBank bank = new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves);
    instruments.clear();
    waves.clear();

    List<MidiEvent> events = new ArrayList<MidiEvent>();
    events.add(new MidiEvent(0, 0, 0, 0x90, 0, 60, 100, -1, null));
    MidiSong song = new MidiSong("self", 0, 96, events, 0);
    events.clear();
    return instrument.regions.size() == 1 && bank.instruments.size() == 1 && bank.waves.size() == 1
            && region.sample == waveSample && song.events.size() == 1 && song.countStatus(0x91) == 1;
}

static boolean renderBoundsSelfCheck() {
    DlsBank empty = new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>());
    boolean rejectedLength = false;
    boolean rejectedPcmArray = false;
    boolean rejectedWavRate = false;
    try {
        new PreviewRenderer(empty, Integer.MAX_VALUE, 2);
    } catch (IllegalArgumentException expected) {
        rejectedLength = true;
    }
    try {
        MidiSong longSong = new MidiSong("self", 0, 96, new ArrayList<MidiEvent>(), 1200000000000000L);
        MobileBae.renderPreview(empty, longSong, 1, Integer.MAX_VALUE);
    } catch (IllegalArgumentException expected) {
        rejectedPcmArray = true;
    }
    try {
        MobileBae.wavBytes(new short[0], 0);
    } catch (Exception expected) {
        rejectedWavRate = true;
    }
    return rejectedLength && rejectedPcmArray && rejectedWavRate;
}

static boolean lfoEg2SourceSelfCheck() {
    Articulation articulation = new Articulation();
    Connection lfoPitch = new Connection(1, 0, 3, 0x4000, 6553600);
    Connection eg2Pitch = new Connection(5, 0, 3, 0x4000, 6553600);
    articulation.runtimeConnections.add(lfoPitch);
    articulation.runtimeConnections.add(eg2Pitch);
    articulation.eg2Attack = 1000000;
    Wave wave = new Wave(0, 1, 1, 22050, 16, 2, -1, new short[]{0, 0, 0}, new SampleInfo());
    Voice voice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), articulation, 60, 127,
            new ChannelState(0), 1000);
    int lfo0 = voice.runtimeConnectionValueQ16(lfoPitch);
    voice.modulationLfo.next();
    voice.modulationLfo.next();
    voice.modulationLfo.next();
    int lfo1 = voice.runtimeConnectionValueQ16(lfoPitch);
    int eg20 = voice.runtimeConnectionValueQ16(eg2Pitch);
    voice.eg2Envelope.next();
    voice.eg2Envelope.next();
    int eg21 = voice.runtimeConnectionValueQ16(eg2Pitch);
    return lfo0 != lfo1 && eg20 < eg21;
}

static boolean voiceControlQuantumSelfCheck() {
    int blockFrames = defaultRenderBlockFrames(22050);
    Articulation articulation = new Articulation();
    articulation.eg1Attack = 100000;
    int frames = blockFrames + 3;
    short[] pcm = new short[frames + 1];
    Arrays.fill(pcm, (short) 1024);
    Wave wave = new Wave(0, 1, 1, 22050, 16, frames, -1, pcm, new SampleInfo());
    Voice voice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), articulation, 60, 127,
            new ChannelState(0), 22050);
    int initialTick = voice.envelope.tickIndex;
    for (int i = 0; i < blockFrames; i++) {
        voice.next();
    }
    boolean heldInsideBlock = voice.envelope.tickIndex == initialTick;
    voice.next();
    return defaultRenderBlockFrames(44100) == 441
            && heldInsideBlock && voice.envelope.tickIndex == initialTick + 10000;
}

static boolean eg1MultiplierSelfCheck() {
    Envelope eg1 = new Envelope(0, 0, 0, 1000000, 0, 1000000, true);
    int first = eg1.next();
    int tenth = 0;
    for (int i = 0; i < 9; i++) {
        tenth = eg1.next();
    }
    Envelope eg2 = new Envelope(0, 0, 0, 1000000, 0x8000, 1000000, false);
    for (int i = 0; i < 101; i++) {
        eg2.next();
    }
    Envelope eg2Release = new Envelope(0, 0, 0, 2999000, 0, 199000, false);
    eg2Release.current = 40096;
    eg2Release.release(eg2Release.releaseMicros);
    eg2Release.next();
    return eg1Multiplier(20000) == 2
            && eg1Multiplier(1000000) == 57825
            && eg1SustainTarget(0) == 0
            && eg1SustainTarget(0x10000) == 0xFFFF0000L
            && (eg1SustainTarget(0x8000) >>> 16) == 255
            && first == 0x10000
            && tenth < 0x6000
            && eg2.current == 0x7FFF
            && eg2Release.current == 40191;
}

static boolean envelopeDls2StageSelfCheck() {
    Articulation articulation = new Articulation();
    articulation.apply(new Connection(0, 0, 0x20B, 0, -261248190));
    articulation.apply(new Connection(0, 0, 0x20C, 0, -261248190));
    articulation.apply(new Connection(0, 0, 0x30F, 0, -261248190));
    articulation.apply(new Connection(0, 0, 0x310, 0, -261248190));
    Envelope eg1Delay = new Envelope(articulation.eg1Delay, 0, 0, 0, 0, 1000000, true);
    Envelope eg2Delay = new Envelope(articulation.eg2Delay, 0, 0, 0, 0, 1000000, false);
    for (int i = 0; i < 10; i++) {
        if (eg1Delay.next() != 0 || eg2Delay.next() != 0) {
            return false;
        }
    }
    boolean delayHeld = eg1Delay.stage == Envelope.DELAY && eg2Delay.stage == Envelope.DELAY;
    boolean delayTransition = eg1Delay.next() == 0 && eg2Delay.next() == 0
            && eg1Delay.stage == Envelope.ATTACK && eg2Delay.stage == Envelope.ATTACK;
    eg1Delay.next();
    eg2Delay.next();
    boolean delayFinished = eg1Delay.stage == Envelope.FINISHED && eg2Delay.stage == Envelope.SUSTAIN;

    Envelope eg1 = new Envelope(0, 0, articulation.eg1Hold, 0, 0, 1000000, true);
    Envelope eg2 = new Envelope(0, 0, articulation.eg2Hold, 0, 0, 1000000, false);
    int eg1LastHeld = 0;
    int eg2LastHeld = 0;
    for (int i = 0; i < 10; i++) {
        eg1LastHeld = eg1.next();
        eg2LastHeld = eg2.next();
    }
    int eg1Transition = eg1.next();
    int eg2Transition = eg2.next();
    int eg1AfterHold = eg1.next();
    int eg2AfterHold = eg2.next();
    Envelope eg1Shutdown = new Envelope(0, 0, 0, 0, 0x10000, 1000000, true);
    Envelope eg2Shutdown = new Envelope(0, 0, 0, 0, 0x10000, 1000000, false);
    eg1Shutdown.next();
    eg2Shutdown.next();
    eg1Shutdown.shutdown();
    eg2Shutdown.shutdown();
    boolean shutdownStarted = eg1Shutdown.stage == Envelope.SHUTDOWN
            && eg2Shutdown.stage == Envelope.SHUTDOWN
            && eg1Shutdown.activeReleaseMicros == Envelope.FORCED_FADE_MICROS
            && eg2Shutdown.activeReleaseMicros == Envelope.FORCED_FADE_MICROS;
    eg1Shutdown.next();
    eg2Shutdown.next();
    eg1Shutdown.next();
    int eg2ShutdownMiddle = eg2Shutdown.next();
    eg1Shutdown.next();
    eg2Shutdown.next();
    return articulation.eg1Delay == articulation.eg2Delay
            && articulation.eg1Hold == articulation.eg2Hold
            && articulation.eg1Delay >= 90000 && articulation.eg1Delay <= 110000
            && delayHeld && delayTransition && delayFinished
            && eg1LastHeld == 0x10000 && eg2LastHeld == Envelope.EG2_FULL
            && eg1Transition == 0x10000 && eg2Transition == Envelope.EG2_FULL
            && eg1AfterHold == 0 && eg2AfterHold == 0
            && eg1.stage == Envelope.FINISHED && eg2.stage == Envelope.SUSTAIN
            && shutdownStarted && eg2ShutdownMiddle > 21000 && eg2ShutdownMiddle < 23000
            && eg1Shutdown.finished && eg2Shutdown.finished;
}

static boolean gainRampSelfCheck() {
    int blockFrames = defaultRenderBlockFrames(22050);
    Articulation articulation = new Articulation();
    articulation.addDefaultConnections();
    int frames = blockFrames * 2 + 4;
    short[] pcm = new short[frames + 1];
    Arrays.fill(pcm, (short) 1024);
    Wave wave = new Wave(0, 1, 1, 22050, 16, frames, -1, pcm, new SampleInfo());
    ChannelState channel = new ChannelState(0);
    Voice voice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), articulation, 60, 127,
            channel, 22050);
    int startGain = voice.leftGain;
    channel.volume = 1;
    for (int i = 0; i < blockFrames; i++) {
        voice.next();
    }
    voice.next();
    int firstRampGain = voice.leftGain;
    voice.next();
    voice.next();
    voice.next();
    int fourthRampGain = voice.leftGain;
    voice.next();
    int fifthRampGain = voice.leftGain;
    int expectedFifth = startGain + (((voice.targetLeftGain - startGain) << 8) / blockFrames) / 64;
    return firstRampGain == startGain && fourthRampGain == startGain && fifthRampGain == expectedFifth;
}

static boolean panAccumulatorSelfCheck() {
    Articulation articulation = new Articulation();
    articulation.addDefaultConnections();
    Wave wave = new Wave(0, 1, 1, 22050, 16, 2, -1, new short[]{0, 0, 0}, new SampleInfo());
    ChannelState leftChannel = new ChannelState(0);
    leftChannel.pan = 0;
    Voice left = new Voice(0, 60, 0, 0, wave, new SampleInfo(), articulation, 60, 127,
            leftChannel, 22050);
    ChannelState rightChannel = new ChannelState(0);
    rightChannel.pan = 127;
    Voice right = new Voice(0, 60, 0, 0, wave, new SampleInfo(), articulation, 60, 127,
            rightChannel, 22050);
    return panScaleQ16(-0x10000) == 0 && panScaleQ16(0) == 46341 && panScaleQ16(0x10000) == 65535
            && left.leftGain > left.rightGain && right.rightGain > right.leftGain;
}

static boolean resetControllersSelfCheck() {
    PreviewRenderer renderer = new PreviewRenderer(new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>()), 22050, 1);
    ChannelState channel = renderer.channels[0];
    channel.bankMsb = 12;
    channel.bankLsb = 34;
    channel.program = 56;
    channel.volume = 1;
    channel.expression = 2;
    channel.pan = 3;
    channel.sustain = true;
    channel.rpnValues[0] = 99;
    renderer.controller(channel, 121, 0);
    return channel.bankMsb == 12 && channel.bankLsb == 34 && channel.program == 56
            && channel.volume == 100 && channel.expression == 127 && channel.pan == 64
            && !channel.sustain && channel.rpnValues[0] == 0x0100;
}

static boolean footControllerSelfCheck() {
    PreviewRenderer renderer = new PreviewRenderer(new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>()), 22050, 1);
    ChannelState channel = renderer.channels[0];
    renderer.controller(channel, 4, 12);
    renderer.controller(channel, 36, 34);
    boolean stored = channel.foot == 12 && channel.footLsb == 34;
    renderer.controller(channel, 121, 0);
    return stored && channel.foot == 0 && channel.footLsb == 0;
}

static boolean bankSelectResetSelfCheck() {
    Articulation articulation = new Articulation();
    articulation.addDefaultConnections();
    List<Wave> waves = new ArrayList<Wave>();
    waves.add(new Wave(0, 1, 1, 22050, 16, 20, -1, new short[21], new SampleInfo()));
    waves.add(new Wave(1, 1, 1, 22050, 16, 20, -1, new short[21], new SampleInfo()));

    Region bank0Region = new Region(false, articulation);
    bank0Region.tableIndex = 0;
    List<Region> bank0Regions = new ArrayList<Region>();
    bank0Regions.add(bank0Region);
    Region bank5Region = new Region(false, articulation);
    bank5Region.tableIndex = 1;
    List<Region> bank5Regions = new ArrayList<Region>();
    bank5Regions.add(bank5Region);

    List<Instrument> instruments = new ArrayList<Instrument>();
    instruments.add(new Instrument(121 << 8, 0, true, articulation, bank0Regions));
    instruments.add(new Instrument((121 << 8) | 5, 0, true, articulation, bank5Regions));
    PreviewRenderer renderer = new PreviewRenderer(new DlsBank("self", "DLS ", 2, 0, 0, instruments, waves),
            22050, 1);
    ChannelState channel = renderer.channels[0];
    PreviewRenderer bank5Renderer = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0,
            Collections.singletonList(new Instrument((121 << 8) | 5, 0, true, articulation, bank5Regions)),
            waves), 22050, 1);
    bank5Renderer.controller(bank5Renderer.channels[0], 32, 5);
    bank5Renderer.programChange(bank5Renderer.channels[0], 0);
    bank5Renderer.noteOn(0, 60, 100);
    PreviewRenderer customMissRenderer = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0,
            Collections.singletonList(new Instrument(121 << 8, 0, true, articulation, bank0Regions)),
            waves), 22050, 1);
    customMissRenderer.controller(customMissRenderer.channels[0], 0, 2);
    customMissRenderer.programChange(customMissRenderer.channels[0], 0);
    customMissRenderer.noteOn(0, 60, 100);
    renderer.controller(channel, 32, 5);
    renderer.controller(channel, 0, 121);
    renderer.programChange(channel, 0);
    renderer.noteOn(0, 60, 100);
    PreviewRenderer latchedRenderer = new PreviewRenderer(new DlsBank("self", "DLS ", 2, 0, 0, instruments, waves),
            22050, 1);
    latchedRenderer.programChange(latchedRenderer.channels[0], 0);
    latchedRenderer.controller(latchedRenderer.channels[0], 32, 5);
    latchedRenderer.noteOn(0, 60, 100);
    return channel.bankLsb == 0 && renderer.voices.size() == 1 && renderer.voices.get(0).wave.index == 0
            && bank5Renderer.voices.size() == 1 && bank5Renderer.voices.get(0).wave.index == 1
            && customMissRenderer.voices.size() == 1 && customMissRenderer.voices.get(0).wave.index == 0
            && latchedRenderer.voices.size() == 1 && latchedRenderer.voices.get(0).wave.index == 0;
}

static boolean programAliasSelfCheck() {
    ByteArrayOutputStream pgal = new ByteArrayOutputStream();
    le32(pgal, 2);
    for (int i = 0; i < 128; i++) {
        pgal.write(i);
    }
    le32(pgal, 1);
    le16(pgal, 121 << 7);
    le16(pgal, 81);
    le16(pgal, 121 << 7);
    le16(pgal, 90);
    DlsParser parser = new DlsParser(pgal.toByteArray(), "alias-self");
    parser.parsePgal(0, pgal.size());

    Articulation articulation = new Articulation();
    articulation.addDefaultConnections();
    Region region = new Region(false, articulation);
    region.tableIndex = 0;
    List<Region> regions = Collections.singletonList(region);
    List<Wave> waves = Collections.singletonList(new Wave(0, 1, 1, 22050, 16, 20, -1,
            new short[21], new SampleInfo()));
    DlsBank bank = new DlsBank("alias-self", "DLS ", 1, 0, 0,
            Collections.singletonList(new Instrument(121 << 8, 90, true, articulation, regions)), waves,
            parser.percussionKeyAliases, parser.programAliasSelectors);
    PreviewRenderer renderer = new PreviewRenderer(bank, 22050, 1);
    renderer.channels[0].program = 81;
    renderer.noteOn(0, 60, 100);
    PreviewRenderer noAlias = new PreviewRenderer(new DlsBank("no-alias-self", "DLS ", 1, 0, 0,
            Collections.singletonList(new Instrument(121 << 8, 90, true, articulation, regions)), waves), 22050, 1);
    noAlias.channels[0].program = 81;
    noAlias.noteOn(0, 60, 100);
    return parser.programAliasSelectors.size() == 1 && bank.instruments.size() == 1
            && bank.programAliasFor(81) == 90 && renderer.voices.size() == 1 && noAlias.voices.isEmpty();
}

static boolean percussionKeyAliasSelfCheck() {
    Articulation articulation = new Articulation();
    articulation.addDefaultConnections();
    Region key56 = new Region(false, articulation);
    key56.keyLow = 56;
    key56.keyHigh = 56;
    key56.tableIndex = 0;
    Region key77 = new Region(false, articulation);
    key77.keyLow = 77;
    key77.keyHigh = 77;
    key77.tableIndex = 1;
    List<Region> regions = Arrays.asList(key56, key77);
    List<Wave> waves = Arrays.asList(
            new Wave(0, 1, 1, 22050, 16, 20, -1, new short[21], new SampleInfo()),
            new Wave(1, 1, 1, 22050, 16, 20, -1, new short[21], new SampleInfo()));
    int[] keyAliases = new int[128];
    for (int i = 0; i < keyAliases.length; i++) {
        keyAliases[i] = i;
    }
    keyAliases[56] = 77;
    List<Instrument> instruments = Arrays.asList(
            new Instrument(120 << 8, 0, true, articulation, regions),
            new Instrument(121 << 8, 0, true, articulation, regions));
    DlsBank bank = new DlsBank("key-alias-self", "DLS ", 2, 0, 0,
            instruments, waves, keyAliases, null);
    PreviewRenderer drumRenderer = new PreviewRenderer(bank, 22050, 1);
    drumRenderer.programChange(drumRenderer.channels[9], 0);
    drumRenderer.noteOn(9, 56, 100);
    boolean drumAliased = drumRenderer.voices.size() == 1 && drumRenderer.voices.get(0).wave.index == 1
            && drumRenderer.voices.get(0).key == 77;
    drumRenderer.handle(new MidiEvent(0, 0, 0, 0x89, 9, 56, 0, -1, null));
    boolean noteOffMapped = !drumRenderer.voices.get(0).keyHeld;
    PreviewRenderer melodicRenderer = new PreviewRenderer(bank, 22050, 1);
    melodicRenderer.programChange(melodicRenderer.channels[0], 0);
    melodicRenderer.noteOn(0, 56, 100);
    return drumAliased && noteOffMapped && melodicRenderer.voices.size() == 1
            && melodicRenderer.voices.get(0).wave.index == 0;
}

static int playableNoteOnCount(DlsBank bank, MidiSong song) {
    ChannelState[] state = new ChannelState[16];
    Instrument[] selected = new Instrument[16];
    boolean[] programSelected = new boolean[16];
    for (int i = 0; i < state.length; i++) {
        state[i] = new ChannelState(i);
    }
    int count = 0;
    for (MidiEvent event : song.events) {
        int high = event.status & 0xF0;
        if (event.channel < 0 || event.channel >= state.length) {
            continue;
        }
        ChannelState ch = state[event.channel];
        if (high == 0xB0) {
            if (event.data1 == 0) {
                ch.bankMsb = event.data2 & 0x7F;
                ch.bankLsb = 0;
            } else if (event.data1 == 32) {
                ch.bankLsb = event.data2 & 0x7F;
            }
        } else if (high == 0xC0) {
            ch.program = event.data1 & 0x7F;
            selected[event.channel] = bank.midiInstrument(ch.bankSelector(), ch.program);
            programSelected[event.channel] = true;
        } else if (high == 0x90 && event.data2 > 0
                && (programSelected[event.channel] ? selected[event.channel]
                        : bank.midiInstrument(ch.bankSelector(), ch.program)) != null) {
            count++;
        }
    }
    return count;
}

static boolean effectSendSelfCheck() {
    Articulation articulation = new Articulation();
    articulation.chorus = 0x4000;
    articulation.runtimeConnections.add(new Connection(0xDB, 0, 0x81, 0, 65536000));
    Wave wave = new Wave(0, 1, 1, 22050, 16, 3, -1,
            new short[]{16000, 16000, 16000, 0}, new SampleInfo());
    PreviewRenderer renderer = new PreviewRenderer(new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>()), 22050, 1);
    Voice voice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), articulation, 60, 127,
            renderer.channels[0], 22050);
    renderer.voices.add(voice);
    int[] mix = new int[2];
    renderer.reverbBus = new int[1];
    renderer.chorusBus = new int[1];
    renderer.mixUntil(mix, 0, 1);
    Articulation equalSends = new Articulation();
    equalSends.reverb = 0x8000;
    equalSends.chorus = 0x8000;
    PreviewRenderer equalRenderer = new PreviewRenderer(new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>()), 22050, 1);
    equalRenderer.voices.add(new Voice(0, 60, 0, 0, wave, new SampleInfo(), equalSends, 60, 127,
            equalRenderer.channels[0], 22050));
    equalRenderer.reverbBus = new int[1];
    equalRenderer.chorusBus = new int[1];
    equalRenderer.mixUntil(new int[2], 0, 1);
    Voice equalVoice = equalRenderer.voices.get(0);
    int expectedReverb = effectSendSample(16000,
            (equalVoice.leftGain + equalVoice.rightGain) >> 1, equalVoice.reverbSend, 24);
    boolean chorusShift = equalRenderer.reverbBus[0] > 0
            && equalRenderer.chorusBus[0] >= equalRenderer.reverbBus[0] * 2
            && equalRenderer.chorusBus[0] <= equalRenderer.reverbBus[0] * 2 + 16000;
    return voice.reverbSend > 0 && voice.chorusSend > 0 && chorusShift
            && equalRenderer.reverbBus[0] == expectedReverb
            && effectSendSample(1000, 0x4000, 0x10000, 24) == 64000
            && effectSendSample(1000, 0x4000, 0x10000, 23) == 128000
            && renderer.reverbBus[0] != 0 && renderer.chorusBus[0] != 0;
}

static boolean effectGateSelfCheck() {
    int blockFrames = defaultRenderBlockFrames(22050);
    ChorusEffect chorus = new ChorusEffect(22050);
    ReverbEffect reverb = new ReverbEffect(22050);
    EffectGate oneTail = new EffectGate(0, blockFrames);
    boolean idle = !oneTail.processThisBlock(false);
    boolean active = oneTail.processThisBlock(true);
    boolean tail = oneTail.processThisBlock(false);
    boolean stopped = !oneTail.processThisBlock(false);
    EffectGate reverbTail = new EffectGate(reverb.tailFrames(), blockFrames);
    reverbTail.processThisBlock(true);
    int tailBlocks = 0;
    while (reverbTail.processThisBlock(false)) {
        tailBlocks++;
    }
    return chorus.tailFrames() > 0 && reverb.tailFrames() == 28665
            && idle && active && tail && stopped && tailBlocks == reverb.tailFrames() / blockFrames + 1;
}

static boolean mixDynamicsSelfCheck() {
    MixDynamics dynamics = new MixDynamics();
    dynamics.targetGain = 0x8000;
    int[] buffer = {1024, 1024, 1024, 1024};
    dynamics.process(buffer, buffer, 2, 2, false);
    int[] addOut = {1, 2, 3, 4};
    dynamics.process(buffer, addOut, 2, 2, true);
    MixDynamics mixr = new MixDynamics(true);
    int[] hot = {8388607, 8388607, 8388607, 8388607};
    mixr.process(hot, hot, 2, 2, false);
    return buffer[0] == 1024 && buffer[1] == 1024 && buffer[2] == 1024 && buffer[3] == 1024
            && dynamics.currentGain == 0x8000
            && addOut[0] == 513 && addOut[1] == 514 && addOut[2] == 515 && addOut[3] == 516
            && mixr.currentGain < 0x10000 && hot[0] > 0 && hot[0] < 8388607;
}

static boolean mixDynamicsSongEndSelfCheck() {
    Articulation articulation = new Articulation();
    articulation.addDefaultConnections();
    Region region = new Region(false, articulation);
    region.tableIndex = 0;
    List<Region> regions = new ArrayList<Region>();
    regions.add(region);
    List<Instrument> instruments = new ArrayList<Instrument>();
    instruments.add(new Instrument(121 << 8, 0, "DLS ", articulation, regions));
    short[] pcm = new short[2001];
    Arrays.fill(pcm, 0, 2000, (short) 512);
    List<Wave> waves = Collections.singletonList(new Wave(0, 1, 1, 1000, 16, 2000, -1,
            pcm, new SampleInfo()));
    DlsBank bank = new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves);
    MidiEvent note = new MidiEvent(0, 0, 0, 0x90, 0, 60, 100, -1, null);
    List<MidiEvent> events = Collections.singletonList(note);
    short[] ended = MobileBae.renderPreview(bank, new MidiSong("ended", 0, 1000, events, 0), 1000, 1,
            false, false, PreviewRenderer.DEFAULT_VOICE_LIMIT, true, null);
    short[] active = MobileBae.renderPreview(bank, new MidiSong("active", 0, 1000, events, 1000000), 1000, 1,
            false, false, PreviewRenderer.DEFAULT_VOICE_LIMIT, true, null);
    int endedPeak = 0;
    int activePeak = 0;
    for (int i = 1000; i < 1020; i += 2) {
        endedPeak = Math.max(endedPeak, Math.abs(ended[i]));
        activePeak = Math.max(activePeak, Math.abs(active[i]));
    }
    return endedPeak > 0 && activePeak > endedPeak;
}

static boolean streamChunkingSelfCheck() {
    Articulation articulation = new Articulation();
    articulation.addDefaultConnections();
    Region region = new Region(false, articulation);
    region.tableIndex = 0;
    List<Region> regions = new ArrayList<Region>();
    regions.add(region);
    List<Instrument> instruments = new ArrayList<Instrument>();
    instruments.add(new Instrument(121 << 8, 0, "DLS ", articulation, regions));
    short[] pcm = new short[30001];
    for (int i = 0; i < pcm.length; i++) {
        pcm[i] = (short) (((i % 200) - 100) * 120);
    }
    List<Wave> waves = Collections.singletonList(new Wave(0, 1, 1, 22050, 16, 30000, -1,
            pcm, new SampleInfo()));
    DlsBank bank = new DlsBank("stream-self", "DLS ", 1, 0, 0, instruments, waves);
    MidiEvent on = new MidiEvent(0, 0, 0, 0x90, 0, 60, 100, -1, null);
    MidiEvent off = new MidiEvent(0, 0, 1, 0x80, 0, 60, 0, -1, null);
    off.micros = 350000;
    MidiSong song = new MidiSong("stream-self", 0, 1000, Arrays.asList(on, off), 500000);
    short[] whole = MobileBae.renderPreview(bank, song, 22050, 1, false, false,
            PreviewRenderer.DEFAULT_VOICE_LIMIT, true, null);
    PcmStream stream = MobileBae.openStream(bank, song, 22050, 1, false, false,
            PreviewRenderer.DEFAULT_VOICE_LIMIT, true, null);
    short[] split = new short[stream.totalFrames() * 2];
    int[] chunks = {1, 3, stream.blockFrames() / 2 + 1, 7, stream.blockFrames() + 5};
    int frame = 0;
    int chunk = 0;
    while (frame < stream.totalFrames()) {
        int request = Math.min(chunks[chunk++ % chunks.length], stream.totalFrames() - frame);
        int read = stream.read(split, frame, request);
        if (read != request) {
            return false;
        }
        frame += read;
    }
    return stream.finished() && stream.positionFrames() == stream.totalFrames() && Arrays.equals(whole, split);
}

static boolean stereoSourceSelfCheck() {
    Wave wave = new Wave(0, 1, 2, 22050, 16, 2, -1,
            new short[]{16000, 0, 16000, 0, 0, 0}, new SampleInfo());
    PreviewRenderer renderer = new PreviewRenderer(new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>()), 22050, 1);
    Voice voice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), new Articulation(), 60, 127,
            renderer.channels[0], 22050);
    renderer.voices.add(voice);
    int[] mix = new int[2];
    renderer.reverbBus = new int[1];
    renderer.chorusBus = new int[1];
    renderer.mixUntil(mix, 0, 1);

    Articulation sendArticulation = new Articulation();
    sendArticulation.reverb = 0x10000;
    Wave opposite = new Wave(0, 1, 2, 22050, 16, 2, -1,
            new short[]{16000, -16000, 16000, -16000, 0, 0}, new SampleInfo());
    PreviewRenderer sendRenderer = new PreviewRenderer(new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>()), 22050, 1);
    Voice sendVoice = new Voice(0, 60, 0, 0, opposite, new SampleInfo(), sendArticulation, 60, 127,
            sendRenderer.channels[0], 22050);
    sendRenderer.voices.add(sendVoice);
    int[] sendMix = new int[2];
    sendRenderer.reverbBus = new int[1];
    sendRenderer.chorusBus = new int[1];
    sendRenderer.mixUntil(sendMix, 0, 1);

    Articulation oddSendArticulation = new Articulation();
    oddSendArticulation.reverb = 0x10000;
    Wave oddOpposite = new Wave(0, 1, 2, 22050, 16, 2, -1,
            new short[]{1, -1, 1, -1, 0, 0}, new SampleInfo());
    PreviewRenderer oddSendRenderer = new PreviewRenderer(new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>()), 22050, 1);
    oddSendRenderer.voices.add(new Voice(0, 60, 0, 0, oddOpposite, new SampleInfo(), oddSendArticulation,
            60, 127, oddSendRenderer.channels[0], 22050));
    oddSendRenderer.reverbBus = new int[1];
    oddSendRenderer.chorusBus = new int[1];
    oddSendRenderer.mixUntil(new int[2], 0, 1);

    Articulation articulation = new Articulation();
    Region region = new Region(false, articulation);
    region.tableIndex = 0;
    List<Region> regions = new ArrayList<Region>();
    regions.add(region);
    List<Instrument> instruments = new ArrayList<Instrument>();
    instruments.add(new Instrument(121 << 8, 0, "DLS ", articulation, regions));
    List<Wave> waves = new ArrayList<Wave>();
    waves.add(new Wave(0, 1, 3, 22050, 16, 1, -1, new short[]{1, 2, 3, 0, 0, 0}, new SampleInfo()));
    boolean rejectedInvalidChannels = false;
    try {
        new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves);
    } catch (IllegalArgumentException expected) {
        rejectedInvalidChannels = true;
    }

    return mix[0] != 0 && mix[1] == 0 && renderer.reverbBus[0] == 0 && renderer.chorusBus[0] == 0
            && sendMix[0] > 0 && sendMix[1] < 0 && sendRenderer.reverbBus[0] == 0
            && stereoToMonoSample(1, -1) == -1 && oddSendRenderer.reverbBus[0] < 0
            && rejectedInvalidChannels;
}

static boolean allNotesControllerSelfCheck() {
    int blockFrames = defaultRenderBlockFrames(22050);
    Articulation articulation = new Articulation();
    Region region = new Region(false, articulation);
    region.tableIndex = 0;
    List<Region> regions = new ArrayList<Region>();
    regions.add(region);
    List<Instrument> instruments = new ArrayList<Instrument>();
    instruments.add(new Instrument(121 << 8, 0, "DLS ", articulation, regions));
    List<Wave> waves = new ArrayList<Wave>();
    waves.add(new Wave(0, 1, 1, 22050, 16, blockFrames + 2, -1,
            new short[blockFrames + 3], new SampleInfo()));

    PreviewRenderer notesOff = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves),
            22050, 1);
    notesOff.noteOn(0, 60, 100);
    notesOff.controller(notesOff.channels[0], 123, 0);
    boolean allNotesDelayed = !notesOff.voices.isEmpty()
            && !notesOff.voices.get(0).keyHeld
            && notesOff.voices.get(0).envelope.stage != Envelope.RELEASE;

    PreviewRenderer modeReset = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves),
            22050, 1);
    modeReset.noteOn(0, 60, 100);
    modeReset.controller(modeReset.channels[0], 124, 0);
    boolean modeDelayed = !modeReset.voices.isEmpty()
            && !modeReset.voices.get(0).keyHeld
            && modeReset.voices.get(0).envelope.stage != Envelope.RELEASE;

    PreviewRenderer soundOff = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves),
            22050, 1);
    soundOff.noteOn(0, 60, 100);
    soundOff.controller(soundOff.channels[0], 120, 0);
    return allNotesDelayed && modeDelayed && !soundOff.voices.get(0).active;
}

static boolean mipSelfCheck() {
    PreviewRenderer renderer = new PreviewRenderer(new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>()), 22050, 1);
    int gatedThreshold = Math.min(PreviewRenderer.DEFAULT_VOICE_LIMIT + 1, 0x7F);
    renderer.handle(new MidiEvent(0, 0, 0, 0xF0, -1, -1, -1, -1,
            new byte[]{0x7F, 0x00, 0x0B, 0x01, 0x02, 0x10, 0x03, (byte) gatedThreshold}));
    boolean active = renderer.noteAllowed(2);
    boolean mipCapacityMatched = PreviewRenderer.DEFAULT_VOICE_LIMIT >= 0x7F
            ? renderer.noteAllowed(3) : !renderer.noteAllowed(3);
    renderer.handle(new MidiEvent(0, 0, 1, 0xB3, 3, 7, 1, -1, null));
    boolean controllerKept = renderer.channels[3].volume == 1;
    renderer.handle(new MidiEvent(0, 0, 2, 0xF0, -1, -1, -1, -1,
            new byte[]{0x7F, 0x00, 0x0B, 0x01, 0x10, 0x01}));
    return active && mipCapacityMatched && controllerKept && renderer.noteAllowed(3);
}

static boolean globalSysExSelfCheck() {
    PreviewRenderer renderer = new PreviewRenderer(new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>()), 22050, 1);
    renderer.handle(new MidiEvent(0, 0, 0, 0xF0, -1, -1, -1, -1,
            new byte[]{0x7F, 0x7F, 0x04, 0x01, 0x00, 0x20}));
    renderer.handle(new MidiEvent(0, 0, 1, 0xF0, -1, -1, -1, -1,
            new byte[]{0x7F, 0x7F, 0x04, 0x03, 0x00, 0x50}));
    renderer.handle(new MidiEvent(0, 0, 2, 0xF0, -1, -1, -1, -1,
            new byte[]{0x7F, 0x7F, 0x04, 0x04, 0x00, 0x50}));
    PreviewRenderer neutral = new PreviewRenderer(new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>()), 22050, 1);
    Wave wave = new Wave(0, 1, 1, 22050, 16, 2, -1, new short[]{0, 0, 0}, new SampleInfo());
    Voice neutralVoice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), new Articulation(), 60, 127,
            neutral.channels[0], 22050);
    Voice tunedVoice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), new Articulation(), 60, 127,
            renderer.channels[0], 22050);
    return renderer.masterVolumeQ16 < 0x10000
            && renderer.globalFinePitchQ16() > 0
            && renderer.globalCoarseSemitones() > 0
            && tunedVoice.leftGain < neutralVoice.leftGain
            && tunedVoice.updateRuntimeModulation(0x10000) > neutralVoice.updateRuntimeModulation(0x10000);
}

static boolean systemModeSysExSelfCheck() {
    PreviewRenderer renderer = new PreviewRenderer(new DlsBank("self", "DLS ", 0, 0, 0,
            new ArrayList<Instrument>(), new ArrayList<Wave>()), 22050, 1);
    renderer.voices.add(new Voice(0, 60, 0, 0,
            new Wave(0, 1, 1, 22050, 16, 2, -1, new short[]{0, 0, 0}, new SampleInfo()),
            new SampleInfo(), new Articulation(), 60, 127, renderer.channels[0], 22050));
    renderer.handle(new MidiEvent(0, 0, 0, 0xF0, -1, -1, -1, -1,
            new byte[]{0x7E, 0x7F, 0x09, 0x01}));
    return renderer.voices.isEmpty();
}

static boolean voiceLimitSelfCheck() {
    int limit = PreviewRenderer.DEFAULT_VOICE_LIMIT;
    Articulation articulation = new Articulation();
    articulation.addDefaultConnections();
    Region region = new Region(false, articulation);
    region.options = 0;
    region.tableIndex = 0;
    List<Region> regions = new ArrayList<Region>();
    regions.add(region);
    List<Instrument> instruments = new ArrayList<Instrument>();
    instruments.add(new Instrument(121 << 8, 0, "DLS ", articulation, regions));
    List<Wave> waves = new ArrayList<Wave>();
    waves.add(new Wave(0, 1, 1, 22050, 16, 20, -1, new short[21], new SampleInfo()));
    PreviewRenderer renderer = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves),
            22050, 1);
    renderer.noteOn(0, 60, 100);
    Voice firstVoice = renderer.voices.get(0);
    for (int i = 0; i < limit + 4; i++) {
        renderer.noteOn(0, 60 + (i % 68), 100);
    }
    boolean limited = renderer.voices.size() == limit && !renderer.voices.contains(firstVoice);

    PreviewRenderer stealRenderer = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves),
            22050, 1);
    for (int i = 0; i < limit; i++) {
        stealRenderer.noteOn(0, 60 + (i % 68), 100);
    }
    Voice releasedVoice = stealRenderer.voices.get(limit - 1);
    releasedVoice.noteOff();
    stealRenderer.noteOn(0, 127, 100);
    boolean stoleReleased = stealRenderer.voices.size() == limit && !stealRenderer.voices.contains(releasedVoice);

    PreviewRenderer activeRenderer = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves),
            22050, 1);
    activeRenderer.channels[15].program = 0;
    activeRenderer.noteOn(15, 40, 100);
    for (int i = 0; i < limit - 1; i++) {
        activeRenderer.noteOn(0, 60 + (i % 68), 100);
    }
    Voice oldestCh0 = activeRenderer.voices.get(1);
    activeRenderer.noteOn(1, 127, 100);
    boolean hasCh15 = false;
    boolean hasCh0 = false;
    boolean hasCh1 = false;
    for (Voice voice : activeRenderer.voices) {
        hasCh15 |= voice.channel == 15;
        hasCh0 |= voice.channel == 0;
        hasCh1 |= voice.channel == 1;
    }
    boolean stolePriorityChannel = activeRenderer.voices.size() == limit
            && hasCh15 && hasCh0 && hasCh1 && !activeRenderer.voices.contains(oldestCh0);

    PreviewRenderer customRenderer = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves),
            22050, 1, true, true, 3, true, null);
    customRenderer.noteOn(0, 60, 100);
    Voice customFirst = customRenderer.voices.get(0);
    for (int i = 0; i < 6; i++) {
        customRenderer.noteOn(0, 61 + i, 100);
    }
    boolean customLimited = customRenderer.voices.size() == 3 && !customRenderer.voices.contains(customFirst);
    return limited && stoleReleased && stolePriorityChannel && customLimited;
}

static boolean vibrationFilterSelfCheck() {
    Articulation articulation = new Articulation();
    articulation.addDefaultConnections();
    Region region = new Region(false, articulation);
    region.tableIndex = 0;
    List<Region> regions = new ArrayList<Region>();
    regions.add(region);
    List<Instrument> instruments = new ArrayList<Instrument>();
    instruments.add(new Instrument(0, VIBRATION_PROGRAM, "DLS ", articulation, regions));
    List<Wave> waves = new ArrayList<Wave>();
    waves.add(new Wave(0, 1, 1, 1000, 16, 1000, -1, new short[1001], new SampleInfo()));
    DlsBank bank = new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves);

    MidiEvent program = new MidiEvent(0, 0, 0, 0xC0, 0, VIBRATION_PROGRAM, 0, -1, null);
    MidiEvent on = new MidiEvent(0, 0, 1, 0x90, 0, 60, 127, -1, null);
    MidiEvent off = new MidiEvent(100, 0, 2, 0x80, 0, 60, 0, -1, null);
    off.micros = 100000;
    List<MidiEvent> events = new ArrayList<MidiEvent>();
    events.add(program);
    events.add(on);
    events.add(off);
    MidiSong song = new MidiSong("vibration-self", 0, 1000, events, 200000);

    final int[] callback = new int[4];
    PcmStream filtered = MobileBae.openStream(bank, song, 1000, 1, false, false, 16, true, new VibrationListener() {
        public void vibration(long micros, int channel, int key, int velocity, boolean on) {
            callback[on ? 0 : 1]++;
            callback[2] = key;
            if (on) {
                callback[3] = velocity;
            }
        }
    });
    filtered.read(new short[400], 0, 200);
    boolean filteredOut = filtered.renderer.voices.isEmpty()
            && callback[0] == 1 && callback[1] == 1 && callback[2] == 60 && callback[3] == 127;

    PcmStream unfiltered = MobileBae.openStream(bank, song, 1000, 1, false, false, 16, false, null);
    unfiltered.read(new short[2], 0, 1);
    return filteredOut && unfiltered.renderer.voices.size() == 1;
}

static boolean exclusiveVoiceSelfCheck() {
    Articulation articulation = new Articulation();
    articulation.addDefaultConnections();
    articulation.eg1Release = 500000;
    articulation.eg2Release = 500000;
    Region region = new Region(false, articulation);
    region.keyGroup = 7;
    region.tableIndex = 0;
    List<Region> regions = new ArrayList<Region>();
    regions.add(region);
    List<Instrument> instruments = new ArrayList<Instrument>();
    instruments.add(new Instrument(121 << 8, 0, "DLS ", articulation, regions));
    List<Wave> waves = new ArrayList<Wave>();
    waves.add(new Wave(0, 1, 1, 22050, 16, 200, -1, new short[201], new SampleInfo()));
    PreviewRenderer renderer = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves),
            22050, 1);
    renderer.noteOn(0, 60, 100);
    renderer.noteOn(0, 64, 100);
    boolean keyGroupRelease = renderer.voices.size() == 2 && !renderer.voices.get(0).keyHeld
            && renderer.voices.get(0).envelope.stage == Envelope.SHUTDOWN
            && renderer.voices.get(0).envelope.activeReleaseMicros == Envelope.FORCED_FADE_MICROS
            && renderer.voices.get(1).key == 64;

    region.keyGroup = 0;
    region.options = Region.OPTION_SELF_EXCLUSIVE;
    PreviewRenderer sameKeyRenderer = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0, instruments, waves),
            22050, 1);
    sameKeyRenderer.noteOn(0, 60, 100);
    sameKeyRenderer.noteOn(0, 60, 100);
    boolean sameRegionRelease = sameKeyRenderer.voices.size() == 2
            && !sameKeyRenderer.voices.get(0).keyHeld
            && sameKeyRenderer.voices.get(0).envelope.stage == Envelope.SHUTDOWN
            && sameKeyRenderer.voices.get(0).envelope.activeReleaseMicros == Envelope.FORCED_FADE_MICROS
            && sameKeyRenderer.voices.get(1).key == 60;
    Voice killedVoice = sameKeyRenderer.voices.get(0);
    killedVoice.tickControl();
    killedVoice.tickControl();
    killedVoice.tickControl();
    boolean forcedFadeFinished = !killedVoice.active;

    region.options = 0;
    PreviewRenderer nonExclusiveRenderer = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0,
            instruments, waves), 22050, 1);
    nonExclusiveRenderer.noteOn(0, 60, 100);
    nonExclusiveRenderer.noteOn(0, 60, 100);
    boolean selfNonExclusive = nonExclusiveRenderer.voices.get(0).keyHeld;
    Voice normalReleaseVoice = nonExclusiveRenderer.voices.get(0);
    normalReleaseVoice.noteOff();
    normalReleaseVoice.tickControl();
    boolean normalRelease = normalReleaseVoice.envelope.stage == Envelope.RELEASE
            && normalReleaseVoice.envelope.activeReleaseMicros == normalReleaseVoice.envelope.releaseMicros;

    Region lowVelocity = new Region(false, articulation);
    lowVelocity.keyLow = 60;
    lowVelocity.keyHigh = 60;
    lowVelocity.velocityLow = 0;
    lowVelocity.velocityHigh = 63;
    lowVelocity.tableIndex = 0;
    lowVelocity.index = 0;
    Region highVelocity = new Region(false, articulation);
    highVelocity.keyLow = 60;
    highVelocity.keyHigh = 60;
    highVelocity.velocityLow = 64;
    highVelocity.velocityHigh = 127;
    highVelocity.options = 0x10;
    highVelocity.tableIndex = 0;
    highVelocity.index = 1;
    List<Region> layeredRegions = new ArrayList<Region>();
    layeredRegions.add(lowVelocity);
    layeredRegions.add(highVelocity);
    List<Instrument> layeredInstruments = new ArrayList<Instrument>();
    layeredInstruments.add(new Instrument(121 << 8, 0, "DLS ", articulation, layeredRegions));
    PreviewRenderer layeredRenderer = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0,
            layeredInstruments, waves), 22050, 1);
    layeredRenderer.noteOn(0, 60, 20);
    layeredRenderer.noteOn(0, 60, 100);
    boolean sameWaveDifferentRegionKept = layeredRenderer.voices.size() == 2
            && layeredRenderer.voices.get(0).active
            && layeredRenderer.voices.get(1).active;

    lowVelocity.keyGroup = 0x11;
    highVelocity.keyGroup = 0x01;
    highVelocity.options = 0;
    PreviewRenderer lowNibbleRenderer = new PreviewRenderer(new DlsBank("self", "DLS ", 1, 0, 0,
            layeredInstruments, waves), 22050, 1);
    lowNibbleRenderer.noteOn(0, 60, 20);
    lowNibbleRenderer.noteOn(0, 60, 100);
    boolean keyGroupLowNibble = lowNibbleRenderer.voices.size() == 2
            && !lowNibbleRenderer.voices.get(0).keyHeld
            && lowNibbleRenderer.voices.get(0).envelope.stage == Envelope.SHUTDOWN
            && lowNibbleRenderer.voices.get(1).active;

    return keyGroupRelease && sameRegionRelease && forcedFadeFinished && selfNonExclusive && normalRelease
            && sameWaveDifferentRegionKept && keyGroupLowNibble;
}

static boolean sampleAttenuationSelfCheck() {
    Wave wave = new Wave(0, 1, 1, 22050, 16, 2, -1, new short[]{0, 0, 0}, new SampleInfo());
    SampleInfo quiet = new SampleInfo();
    quiet.attenuation = -655360;
    SampleInfo loud = new SampleInfo();
    loud.attenuation = 655360;
    Articulation articulation = new Articulation();
    Voice quietVoice = new Voice(0, 60, 0, 0, wave, quiet, articulation, 60, 127, new ChannelState(0), 22050);
    Voice loudVoice = new Voice(0, 60, 0, 0, wave, loud, articulation, 60, 127, new ChannelState(0), 22050);
    return quietVoice.baseGainQ16 < 0x10000 && loudVoice.baseGainQ16 > 0x10000;
}

static boolean noteOnFilterCutoffSelfCheck() {
    Wave wave = new Wave(0, 1, 1, 22050, 16, 2, -1, new short[]{0, 0, 0}, new SampleInfo());
    Articulation articulation = new Articulation();
    articulation.filterCutoff = 4087448;
    articulation.runtimeConnections.add(new Connection(2, 0, 0x500, 0, 131072000));
    Voice voice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), articulation, 60, 100,
            new ChannelState(0), 44100);
    return voice.filter != null && voice.filter.baseCutoff == 5111448
            && voice.filter.effectiveCutoff == 5111448;
}

static boolean eg2FilterCutoffSelfCheck() {
    Wave wave = new Wave(0, 1, 1, 22050, 16, 2, -1, new short[]{0, 0, 0}, new SampleInfo());
    Articulation articulation = new Articulation();
    articulation.eg2Attack = 0;
    articulation.eg2Decay = 2999000;
    articulation.eg2Sustain = 0;
    articulation.filterCutoff = 5111448;
    articulation.runtimeConnections.add(new Connection(5, 0, 0x500, 0, 157286400));
    Voice voice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), articulation, 60, 100,
            new ChannelState(0), 44100);
    for (int i = 0; i < 6; i++) {
        voice.eg2Envelope.next();
    }
    voice.updateRuntimeModulation(0x10000);
    return voice.eg2Envelope.current == 64255 && voice.filter != null
            && voice.filter.effectiveCutoff == 6653568;
}

static boolean sampleGuardFrameSelfCheck() {
    Wave wave = new Wave(0, 1, 1, 22050, 16, 2, -1, new short[]{1000, 2000, 0}, new SampleInfo());
    Voice voice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), new Articulation(), 60, 127,
            new ChannelState(0), 22050);
    int first = voice.next();
    int second = voice.next();
    boolean aliveAfterLastRealFrame = voice.active;
    int third = voice.next();
    boolean rejectedUnguarded = false;
    try {
        new Wave(0, 1, 1, 22050, 16, 2, -1, new short[]{1000, 2000}, new SampleInfo());
    } catch (IllegalArgumentException expected) {
        rejectedUnguarded = true;
    }
    return first != 0 && second != 0 && aliveAfterLastRealFrame && third == 0 && !voice.active
            && rejectedUnguarded;
}

static boolean sourceInterpolationSelfCheck() {
    Wave pcm8 = new Wave(0, 1, 1, 22050, 8, 2, -1, new short[]{0, 256, 0}, new SampleInfo());
    Wave pcm16 = new Wave(0, 1, 1, 22050, 16, 2, -1, new short[]{0, 257, 0}, new SampleInfo());
    return interpolateSourceSample(pcm8, 0, 256, 0x8000) == 0
            && interpolateSourceSample(pcm8, 0, 512, 0x8000) == 256
            && interpolateSourceSample(pcm16, 0, 257, 0x8001) == 128;
}

static boolean loopWrapSelfCheck() {
    Wave wave = new Wave(0, 1, 1, 176400, 16, 4, -1,
            new short[]{1000, 2000, 3000, 4000, 0}, new SampleInfo());
    ByteArrayOutputStream wsmp = new ByteArrayOutputStream();
    le32(wsmp, 20);
    le16(wsmp, 60);
    le16(wsmp, 0);
    le32(wsmp, 0);
    le32(wsmp, 0);
    le32(wsmp, 1);
    le32(wsmp, 16);
    le32(wsmp, 0);
    le32(wsmp, 1);
    le32(wsmp, 2);
    SampleInfo loop = new SampleInfo();
    new DlsParser(wsmp.toByteArray(), "wsmp-loop-self").parseWsmp(0, wsmp.size(), loop);
    Voice voice = new Voice(0, 60, 0, 0, wave, loop, new Articulation(), 60, 127,
            new ChannelState(0), 22050);
    int first = voice.next();
    int second = voice.next();
    Voice interpolation = new Voice(0, 60, 0, 0, wave, loop, new Articulation(), 60, 127,
            new ChannelState(0), 22050);
    interpolation.position = (2L << 16) + 0x8000L;
    int edge = interpolation.next();
    byte[] releaseWsmp = wsmp.toByteArray();
    releaseWsmp[24] = 1;
    SampleInfo releaseLoop = new SampleInfo();
    new DlsParser(releaseWsmp, "wsmp-release-self").parseWsmp(0, releaseWsmp.length, releaseLoop);
    byte[] smpl = new byte[60];
    smpl[28] = 1;
    smpl[44] = 1;
    smpl[48] = 2;
    SampleInfo smplOverride = new SampleInfo();
    new DlsParser(releaseWsmp, "wsmp-smpl-self").parseWsmp(0, releaseWsmp.length, smplOverride);
    new DlsParser(smpl, "smpl-self").parseSmpl(0, smpl.length, smplOverride);
    Articulation slowRelease = new Articulation();
    slowRelease.eg1Sustain = 0x10000;
    slowRelease.eg1Release = 1000000;
    Voice released = new Voice(0, 60, 0, 0, wave, releaseLoop, slowRelease, 60, 127,
            new ChannelState(0), 22050);
    released.position = (2L << 16) + 0x8000L;
    released.noteOff();
    released.next();
    return first != 0 && second != 0 && voice.active && !loop.loopUntilRelease
            && edge == 3500 && interpolation.position == ((2L << 16) + 0x8000L)
            && releaseLoop.loopMode == LOOP_FORWARD && releaseLoop.loopUntilRelease
            && smplOverride.loopMode == LOOP_FORWARD && !smplOverride.loopUntilRelease
            && released.position > released.loopEnd;
}

static boolean noteOnPitchStepSelfCheck() {
    Articulation articulation = new Articulation();
    articulation.addDefaultConnections();
    SampleInfo sample = new SampleInfo();
    sample.present = true;
    sample.unityNote = 69;
    Wave wave = new Wave(0, 1, 1, 15876, 16, 57, -1, new short[58], new SampleInfo());
    Voice voice = new Voice(0, 64, 0, 0, wave, sample, articulation, 64, 100,
            new ChannelState(0), 44100);
    return voice.baseIncrement == 17674;
}

static boolean sourceIncrementClampSelfCheck() {
    Wave wave = new Wave(0, 1, 1, 22050, 16, 4, -1,
            new short[]{1000, 2000, 3000, 4000, 0}, new SampleInfo());
    Articulation low = new Articulation();
    low.pitch = -(200 << 16);
    Voice lowVoice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), low, 60, 127,
            new ChannelState(0), 22050);
    Articulation high = new Articulation();
    high.pitch = 200 << 16;
    Voice highVoice = new Voice(0, 60, 0, 0, wave, new SampleInfo(), high, 60, 127,
            new ChannelState(0), 22050);
    return lowVoice.currentIncrement == MIN_SOURCE_INCREMENT
            && highVoice.currentIncrement == MAX_SOURCE_INCREMENT;
}

static boolean instChunkSelfCheck() {
    ByteArrayOutputStream wave = new ByteArrayOutputStream();
    ascii(wave, "WAVE");
    ascii(wave, "fmt ");
    le32(wave, 16);
    le16(wave, 1);
    le16(wave, 1);
    le32(wave, 22050);
    le32(wave, 44100);
    le16(wave, 2);
    le16(wave, 16);
    ascii(wave, "inst");
    le32(wave, 7);
    wave.write(72);
    wave.write((byte) -5);
    wave.write((byte) -2);
    wave.write(0);
    wave.write(127);
    wave.write(0);
    wave.write(127);
    wave.write(0);
    ascii(wave, "data");
    le32(wave, 4);
    le16(wave, 0);
    le16(wave, 0);

    ByteArrayOutputStream riff = new ByteArrayOutputStream();
    ascii(riff, "RIFF");
    le32(riff, wave.size());
    riff.write(wave.toByteArray(), 0, wave.size());
    Wave parsed = new DlsParser(riff.toByteArray(), "inst-self").parseWave(0, 0);
    return parsed.sample.present && parsed.sample.unityNote == 72
            && parsed.sample.fineTuneCents == -5
            && parsed.sample.attenuation == -2 * 655360
            && parsed.frames == 2;
}

static boolean waveCompletionSelfCheck() {
    ByteArrayOutputStream alaw = new ByteArrayOutputStream();
    ascii(alaw, "WAVE");
    ascii(alaw, "fmt ");
    le32(alaw, 16);
    le16(alaw, 6);
    le16(alaw, 1);
    le32(alaw, 8000);
    le32(alaw, 8000);
    le16(alaw, 1);
    le16(alaw, 8);
    ascii(alaw, "fact");
    le32(alaw, 4);
    le32(alaw, 1);
    ascii(alaw, "data");
    le32(alaw, 2);
    alaw.write(0xD5);
    alaw.write(0x55);

    ByteArrayOutputStream riff = new ByteArrayOutputStream();
    ascii(riff, "RIFF");
    le32(riff, alaw.size());
    riff.write(alaw.toByteArray(), 0, alaw.size());
    Wave parsed = new DlsParser(riff.toByteArray(), "alaw-self").parseWave(0, 0);

    ByteArrayOutputStream noFact = new ByteArrayOutputStream();
    ascii(noFact, "WAVE");
    ascii(noFact, "fmt ");
    le32(noFact, 16);
    le16(noFact, 6);
    le16(noFact, 1);
    le32(noFact, 8000);
    le32(noFact, 8000);
    le16(noFact, 1);
    le16(noFact, 8);
    ascii(noFact, "data");
    le32(noFact, 1);
    noFact.write(0xD5);

    ByteArrayOutputStream noFactRiff = new ByteArrayOutputStream();
    ascii(noFactRiff, "RIFF");
    le32(noFactRiff, noFact.size());
    noFactRiff.write(noFact.toByteArray(), 0, noFact.size());
    boolean rejectedNoFact = false;
    try {
        new DlsParser(noFactRiff.toByteArray(), "alaw-nofact-self").parseWave(0, 0);
    } catch (IllegalArgumentException expected) {
        rejectedNoFact = true;
    }

    ByteArrayOutputStream wide = new ByteArrayOutputStream();
    ascii(wide, "WAVE");
    ascii(wide, "fmt ");
    le32(wide, 16);
    le16(wide, 1);
    le16(wide, 256);
    le32(wide, 8000);
    le32(wide, 512000);
    le16(wide, 512);
    le16(wide, 16);
    ascii(wide, "data");
    le32(wide, 2);
    le16(wide, 0);

    ByteArrayOutputStream wideRiff = new ByteArrayOutputStream();
    ascii(wideRiff, "RIFF");
    le32(wideRiff, wide.size());
    wideRiff.write(wide.toByteArray(), 0, wide.size());
    boolean rejectedWideChannels = false;
    try {
        new DlsParser(wideRiff.toByteArray(), "wide-channel-self").parseWave(0, 0);
    } catch (IllegalArgumentException expected) {
        rejectedWideChannels = true;
    }

    ByteArrayOutputStream extensible = new ByteArrayOutputStream();
    ascii(extensible, "WAVE");
    ascii(extensible, "fmt ");
    le32(extensible, 40);
    le16(extensible, 0xFFFE);
    le16(extensible, 1);
    le32(extensible, 8000);
    le32(extensible, 16000);
    le16(extensible, 2);
    le16(extensible, 16);
    le16(extensible, 22);
    le16(extensible, 16);
    le32(extensible, 4);
    le32(extensible, 1);
    le16(extensible, 0);
    le16(extensible, 0x0010);
    extensible.write(0x80);
    extensible.write(0x00);
    extensible.write(0x00);
    extensible.write(0xAA);
    extensible.write(0x00);
    extensible.write(0x38);
    extensible.write(0x9B);
    extensible.write(0x71);
    ascii(extensible, "data");
    le32(extensible, 4);
    le16(extensible, 256);
    le16(extensible, -256);

    ByteArrayOutputStream extensibleRiff = new ByteArrayOutputStream();
    ascii(extensibleRiff, "RIFF");
    le32(extensibleRiff, extensible.size());
    extensibleRiff.write(extensible.toByteArray(), 0, extensible.size());
    Wave extensibleParsed = new DlsParser(extensibleRiff.toByteArray(), "extensible-pcm-self").parseWave(0, 0);
    byte[] badExtensibleCbSize = extensibleRiff.toByteArray();
    badExtensibleCbSize[36] = 20;
    badExtensibleCbSize[37] = 0;
    boolean rejectedBadExtensibleCbSize = false;
    try {
        new DlsParser(badExtensibleCbSize, "bad-extensible-cb-self").parseWave(0, 0);
    } catch (IllegalArgumentException expected) {
        rejectedBadExtensibleCbSize = true;
    }
    byte[] badExtensibleGuid = extensibleRiff.toByteArray();
    badExtensibleGuid[44] = 2;
    boolean rejectedBadExtensibleGuid = false;
    try {
        new DlsParser(badExtensibleGuid, "bad-extensible-guid-self").parseWave(0, 0);
    } catch (IllegalArgumentException expected) {
        rejectedBadExtensibleGuid = true;
    }

    return parsed.factFrames == 1 && parsed.frames == 2 && parsed.pcm.length == 3
            && parsed.pcm[0] == 8 && parsed.pcm[1] == -8 && parsed.pcm[2] == 0
            && rejectedNoFact && rejectedWideChannels
            && rejectedBadExtensibleCbSize && rejectedBadExtensibleGuid
            && extensibleParsed.formatTag == 1 && extensibleParsed.frames == 2
            && extensibleParsed.pcm[0] == 256 && extensibleParsed.pcm[1] == -256;
}

static boolean mpegWaveSelfCheck() {
    byte[] payload = Base64.getDecoder().decode(
            "//NAxAATMGKEP08YAAvW27YIeaZpmmaaHubAhhBxCwHcAjAOwVY4zrZ3jx48ePAQBMHwfB/nOD/hiU9/Kef5Tz/Ke/o4IA+D7wfBwEAwoMA+D8uBAQ4Pv0e9IAkggwwAALzAIDaT9yb/80LEDhfxcnzVnGgA4mMRgYKfbWDKAuKAoYqmxp0GGCwYJWpUBkkIOSDpBXQVn8SYL0F2/HaMKMKTv8YYYYmj1Hr/5kXiSMS6XUv/8vF4xLpdSLxeO/5UJA0JQkDQlS+5gFgHsIAAsLj/80DEChZYTigBnwAAKwgqYBqA/GD3ARJhu6OUYFKBNmM6itJhKYAKYf2o8mRkBFxgcoFeZjQBtmCegE5iwQViYDiBFfR6vo2sV+Kc996///b2Vfsfq1f+32///Qr3r02WWPWs4wiRh//zQsQLFoJKjAGbaAAMkIlwmEVQOf3cGr5zAhKbz8HeRB4flMdhKf6h6FAuf+ShoPQuN//koaSUZM0///WmgmnTdP////TdNNzdObvC3//mSBQYGAQKO///4YKJGNEFfIoAa7wOagy3Xv/zQMQNFkqCkAGTaAAE2BtCzX8OUMoTX8cRaJ6SX+ZjhSJo9f/WSJqYj1NDH//LpgXTUyMTxkY///oF5FRemRskXnMjb////TMjY6ZOYpJmIVOgr//4lAIKVUxBTUUzLjEwMFVVVVVV//NCxA4AAANIAcAAAFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV");
    byte[] encoded = new byte[payload.length + 4];
    encoded[0] = (byte) 0xFF;
    encoded[1] = (byte) 0xE3;
    encoded[2] = 0x10;
    System.arraycopy(payload, 0, encoded, 4, payload.length);
    ByteArrayOutputStream wave = new ByteArrayOutputStream();
    ascii(wave, "WAVE");
    ascii(wave, "fmt ");
    le32(wave, 30);
    le16(wave, 85);
    le16(wave, 1);
    le32(wave, 22050);
    le32(wave, 4000);
    le16(wave, 576);
    le16(wave, 0);
    le16(wave, 12);
    le16(wave, 1);
    le32(wave, 2);
    le16(wave, 1152);
    le16(wave, 1);
    le16(wave, 1393);
    ascii(wave, "fact");
    le32(wave, 4);
    le32(wave, 3420);
    ascii(wave, "data");
    le32(wave, encoded.length);
    wave.write(encoded, 0, encoded.length);
    wave.write(0);

    ByteArrayOutputStream riff = new ByteArrayOutputStream();
    ascii(riff, "RIFF");
    le32(riff, wave.size());
    riff.write(wave.toByteArray(), 0, wave.size());
    Wave parsed = new DlsParser(riff.toByteArray(), "mpeg-self").parseWave(0, 0);
    long energy = 0;
    long tailEnergy = 0;
    for (int i = 0; i < parsed.frames; i++) {
        energy += Math.abs((int) parsed.pcm[i]);
        if (i >= 2880) {
            tailEnergy += Math.abs((int) parsed.pcm[i]);
        }
    }
    byte[] layerOne = new byte[64];
    for (int p : new int[]{0, 32}) {
        layerOne[p] = (byte) 0xFF;
        layerOne[p + 1] = (byte) 0xFF;
        layerOne[p + 2] = 0x10;
    }
    byte[] afterTruncatedFrame = new byte[69];
    afterTruncatedFrame[0] = (byte) 0xFF;
    afterTruncatedFrame[1] = (byte) 0xFF;
    afterTruncatedFrame[2] = (byte) 0xE0;
    System.arraycopy(layerOne, 0, afterTruncatedFrame, 5, layerOne.length);
    return parsed.formatTag == 85 && parsed.bitsPerSample == 16 && parsed.factFrames == 3420
            && parsed.frames == 3420 && parsed.pcm.length == 3421 && parsed.pcm[3420] == 0
            && MpegDecoder.scanFrames(encoded) == 3456
            && MpegDecoder.scanFrames(layerOne) == 768
            && MpegDecoder.scanFrames(afterTruncatedFrame) == 768
            && energy > 1000000 && tailEnergy > 1000;
}

static boolean poolTableBaseSelfCheck() {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    ascii(body, "WAVE");
    ascii(body, "fmt ");
    le32(body, 16);
    le16(body, 1);
    le16(body, 1);
    le32(body, 8000);
    le32(body, 16000);
    le16(body, 2);
    le16(body, 16);
    ascii(body, "data");
    le32(body, 4);
    le16(body, 100);
    le16(body, -100);

    ByteArrayOutputStream wave = new ByteArrayOutputStream();
    ascii(wave, "RIFF");
    le32(wave, body.size());
    wave.write(body.toByteArray(), 0, body.size());

    ByteArrayOutputStream correctBase = new ByteArrayOutputStream();
    for (int i = 0; i < 12; i++) {
        correctBase.write(0);
    }
    correctBase.write(wave.toByteArray(), 0, wave.size());
    DlsParser correct = new DlsParser(correctBase.toByteArray(), "ptbl-base-self");
    correct.wvplChunkData = 0;
    correct.poolOffsets = new int[]{8};
    correct.parseWavePool();

    ByteArrayOutputStream legacyBase = new ByteArrayOutputStream();
    for (int i = 0; i < 8; i++) {
        legacyBase.write(0);
    }
    legacyBase.write(wave.toByteArray(), 0, wave.size());
    DlsParser legacy = new DlsParser(legacyBase.toByteArray(), "ptbl-legacy-base-self");
    legacy.wvplChunkData = 0;
    legacy.poolOffsets = new int[]{8};
    boolean rejectedLegacyBase = false;
    try {
        legacy.parseWavePool();
    } catch (IllegalArgumentException expected) {
        rejectedLegacyBase = true;
    }
    return correct.waves.size() == 1 && correct.waves.get(0).frames == 2 && rejectedLegacyBase;
}

static boolean imaWavSelfCheck() {
    ByteArrayOutputStream wave = new ByteArrayOutputStream();
    ascii(wave, "WAVE");
    ascii(wave, "fmt ");
    le32(wave, 16);
    le16(wave, 17);
    le16(wave, 1);
    le32(wave, 8000);
    le32(wave, 8000);
    le16(wave, 5);
    le16(wave, 4);
    ascii(wave, "fact");
    le32(wave, 4);
    le32(wave, 3);
    ascii(wave, "data");
    le32(wave, 5);
    le16(wave, 0);
    wave.write(0);
    wave.write(0);
    wave.write(0x11);
    wave.write(0);

    ByteArrayOutputStream riff = new ByteArrayOutputStream();
    ascii(riff, "RIFF");
    le32(riff, wave.size());
    riff.write(wave.toByteArray(), 0, wave.size());
    Wave parsed = new DlsParser(riff.toByteArray(), "imaw-self").parseWave(0, 0);
    boolean mono = parsed.formatTag == 17 && parsed.frames == 3
            && parsed.pcm[0] == 0 && parsed.pcm[1] == 1 && parsed.pcm[2] == 2
            && parsed.pcm[3] == 0;

    ByteArrayOutputStream stereo = new ByteArrayOutputStream();
    ascii(stereo, "WAVE");
    ascii(stereo, "fmt ");
    le32(stereo, 16);
    le16(stereo, 17);
    le16(stereo, 2);
    le32(stereo, 8000);
    le32(stereo, 8000);
    le16(stereo, 16);
    le16(stereo, 4);
    ascii(stereo, "fact");
    le32(stereo, 4);
    le32(stereo, 9);
    ascii(stereo, "data");
    le32(stereo, 16);
    le16(stereo, 0);
    stereo.write(0);
    stereo.write(0);
    le16(stereo, 0);
    stereo.write(0);
    stereo.write(0);
    stereo.write(0x11);
    stereo.write(0);
    stereo.write(0);
    stereo.write(0);
    stereo.write(0x99);
    stereo.write(0);
    stereo.write(0);
    stereo.write(0);

    ByteArrayOutputStream stereoRiff = new ByteArrayOutputStream();
    ascii(stereoRiff, "RIFF");
    le32(stereoRiff, stereo.size());
    stereoRiff.write(stereo.toByteArray(), 0, stereo.size());
    Wave stereoParsed = new DlsParser(stereoRiff.toByteArray(), "imaw-stereo-self").parseWave(0, 0);
    boolean stereoOk = stereoParsed.frames == 9
            && stereoParsed.pcm[0] == 0 && stereoParsed.pcm[1] == 0
            && stereoParsed.pcm[2] == 1 && stereoParsed.pcm[3] == -1
            && stereoParsed.pcm[4] == 2 && stereoParsed.pcm[5] == -2;
    return mono && stereoOk;
}

static boolean midiDataMaskSelfCheck() {
    byte[] midi = new byte[]{
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96,
            'M', 'T', 'r', 'k', 0, 0, 0, 8,
            0, (byte) 0x90, (byte) 0xC1, (byte) 0xFF,
            0, (byte) 0xFF, 0x2F, 0
    };
    MidiSong song = MidiParser.parse(midi, "midi-mask-self");
    return song.events.size() == 2
            && song.events.get(0).data1 == 0x41
            && song.events.get(0).data2 == 0x7F;
}

static boolean midiSystemEventSelfCheck() {
    byte[] realtime = new byte[]{
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96,
            'M', 'T', 'r', 'k', 0, 0, 0, 6,
            0, (byte) 0xF8,
            0, (byte) 0xFF, 0x2F, 0
    };
    MidiSong song = MidiParser.parse(realtime, "midi-realtime-self");

    byte[] common = new byte[]{
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96,
            'M', 'T', 'r', 'k', 0, 0, 0, 6,
            0, (byte) 0xF1,
            0, (byte) 0xFF, 0x2F, 0
    };
    boolean rejectedCommon = false;
    try {
        MidiParser.parse(common, "midi-common-self");
    } catch (IllegalArgumentException expected) {
        rejectedCommon = true;
    }

    byte[] unterminatedSysEx = new byte[]{
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96,
            'M', 'T', 'r', 'k', 0, 0, 0, 8,
            0, (byte) 0xF0, 1, 2,
            0, (byte) 0xFF, 0x2F, 0
    };
    boolean rejectedUnterminatedSysEx = false;
    try {
        MidiParser.parse(unterminatedSysEx, "midi-sysex-self");
    } catch (IllegalArgumentException expected) {
        rejectedUnterminatedSysEx = true;
    }

    return song.events.size() == 2
            && song.events.get(0).status == 0xF8
            && song.events.get(0).data1 == 0
            && song.events.get(0).data2 == 0
            && rejectedCommon && rejectedUnterminatedSysEx;
}

static boolean riffBoundarySelfCheck() {
    ByteArrayOutputStream riff = new ByteArrayOutputStream();
    ascii(riff, "RIFF");
    le32(riff, 12);
    ascii(riff, "DLS ");
    ascii(riff, "JUNK");
    le32(riff, 8);
    le32(riff, 0);
    le32(riff, 0);
    try {
        DlsParser.parse(riff.toByteArray(), "riff-boundary-self");
        return false;
    } catch (IllegalArgumentException expected) {
        return true;
    }
}

static boolean midiHeaderGateSelfCheck() {
    byte[] format0TwoTracks = new byte[]{
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 2, 0, 96
    };
    byte[] zeroDivision = new byte[]{
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 1, 0, 1, 0, 0
    };
    byte[] unknownFormat = new byte[]{
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 3, 0, 1, 0, 96
    };
    boolean rejectedFormat0 = false;
    boolean rejectedDivision = false;
    boolean rejectedUnknown = false;
    try {
        MidiParser.parse(format0TwoTracks, "midi-format0-self");
    } catch (IllegalArgumentException expected) {
        rejectedFormat0 = true;
    }
    try {
        MidiParser.parse(zeroDivision, "midi-division-self");
    } catch (IllegalArgumentException expected) {
        rejectedDivision = true;
    }
    try {
        MidiParser.parse(unknownFormat, "midi-format-self");
    } catch (IllegalArgumentException expected) {
        rejectedUnknown = true;
    }
    return rejectedFormat0 && rejectedDivision && rejectedUnknown;
}

static boolean midiFinalDeltaClampSelfCheck() {
    byte[] midi = new byte[]{
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 100,
            'M', 'T', 'r', 'k', 0, 0, 0, 5,
            (byte) 0xC6, 0x28, (byte) 0xFF, 0x2F, 0
    };
    MidiSong song = MidiParser.parse(midi, "midi-final-delta-self");
    return song.events.size() == 1
            && song.events.get(0).tick == 8000
            && song.lengthMicros == 40000000L;
}

static boolean midiRuntimeTimingSelfCheck() {
    byte[] midi = new byte[]{
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 1, 0, 2, 0, 96,
            'M', 'T', 'r', 'k', 0, 0, 0, 18,
            0, (byte) 0xFF, 0x51, 3, 0x07, (byte) 0xA1, 0x20,
            0, (byte) 0xFF, 0x51, 3, 0x03, (byte) 0xD0, (byte) 0x90,
            1, (byte) 0xFF, 0x2F, 0,
            'M', 'T', 'r', 'k', 0, 0, 0, 20,
            0, (byte) 0x90, 61, 100,
            0, (byte) 0x80, 61, 0,
            1, (byte) 0x90, 60, 100,
            0, (byte) 0x80, 60, 0,
            0, (byte) 0xFF, 0x2F, 0
    };
    MidiSong song = MidiParser.parse(midi, "midi-runtime-self");
    MidiEvent tickZeroNoteOn = null;
    MidiEvent noteOn = null;
    MidiEvent noteOff = null;
    for (MidiEvent event : song.events) {
        if (event.isNoteOn() && event.data1 == 61) {
            tickZeroNoteOn = event;
        } else if (event.isNoteOn()) {
            noteOn = event;
        } else if (event.isNoteOff()) {
            noteOff = event;
        }
    }
    return song.lengthMicros == 2604L
            && tickZeroNoteOn != null && tickZeroNoteOn.micros == 0L
            && noteOn != null && noteOn.micros == 10000L
            && noteOff != null && noteOff.micros == 10000L;
}

static boolean midiMetaLengthSelfCheck() {
    byte[] badTempo = new byte[]{
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96,
            'M', 'T', 'r', 'k', 0, 0, 0, 10,
            0, (byte) 0xFF, 0x51, 2, 0x07, (byte) 0xA1,
            0, (byte) 0xFF, 0x2F, 0
    };
    byte[] badEot = new byte[]{
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96,
            'M', 'T', 'r', 'k', 0, 0, 0, 5,
            0, (byte) 0xFF, 0x2F, 1, 0
    };
    boolean rejectedTempo = false;
    boolean rejectedEot = false;
    try {
        MidiParser.parse(badTempo, "midi-tempo-len-self");
    } catch (IllegalArgumentException expected) {
        rejectedTempo = true;
    }
    try {
        MidiParser.parse(badEot, "midi-eot-len-self");
    } catch (IllegalArgumentException expected) {
        rejectedEot = true;
    }
    return rejectedTempo && rejectedEot;
}

static boolean midiChunkSkipSelfCheck() {
    byte[] midi = new byte[]{
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96,
            'J', 'U', 'N', 'K', 0, 0, 0, 1, 0,
            'M', 'T', 'r', 'k', 0, 0, 0, 4,
            0, (byte) 0xFF, 0x2F, 0
    };
    MidiSong song = MidiParser.parse(midi, "midi-chunk-skip-self");
    return song.events.size() == 1 && song.events.get(0).metaType == 0x2F;
}

static boolean instrumentRegionCountSelfCheck() {
    byte[] ins = minimalInstrumentBytes(0, 0, 2);

    try {
        new DlsParser(ins, "region-count-self").parseInstrument(0, ins.length);
        return false;
    } catch (IllegalArgumentException expected) {
        return true;
    }
}

static boolean selectorModeSelfCheck() {
    byte[] implicitBytes = minimalInstrumentBytes(0, 10, 1);
    DlsParser implicitParser = new DlsParser(implicitBytes, "selector-implicit-self");
    implicitParser.formType = "DLS ";
    Instrument implicit = implicitParser.parseInstrument(0, implicitBytes.length);
    if (implicit.bankMsb != 121 || implicit.bankLsb != 0 || implicit.program != 10) {
        return false;
    }

    byte[] explicitBytes = minimalInstrumentBytes(120 << 8, 1, 1);
    byte[] rawFollowerBytes = minimalInstrumentBytes(5 << 8, 2, 1);
    ByteArrayOutputStream rawPair = new ByteArrayOutputStream();
    rawPair.write(explicitBytes, 0, explicitBytes.length);
    rawPair.write(rawFollowerBytes, 0, rawFollowerBytes.length);
    DlsParser rawParser = new DlsParser(rawPair.toByteArray(), "selector-raw-self");
    rawParser.formType = "DLS ";
    Instrument explicit = rawParser.parseInstrument(0, explicitBytes.length);
    Instrument rawFollower = rawParser.parseInstrument(explicitBytes.length, rawPair.size());
    if (explicit.bankMsb != 120 || explicit.bankLsb != 0
            || rawFollower.bankMsb != 5 || rawFollower.bankLsb != 0) {
        return false;
    }

    ByteArrayOutputStream mixedPair = new ByteArrayOutputStream();
    mixedPair.write(implicitBytes, 0, implicitBytes.length);
    mixedPair.write(explicitBytes, 0, explicitBytes.length);
    DlsParser mixedParser = new DlsParser(mixedPair.toByteArray(), "selector-mixed-self");
    mixedParser.formType = "DLS ";
    mixedParser.parseInstrument(0, implicitBytes.length);
    try {
        mixedParser.parseInstrument(implicitBytes.length, mixedPair.size());
        return false;
    } catch (IllegalArgumentException expected) {
        return true;
    }
}

static boolean drumProgramResourceLookupSelfCheck() {
    Instrument drumKit = new Instrument(0x80000000, 0, "DLS ", new Articulation(), new ArrayList<Region>());
    List<Instrument> instruments = new ArrayList<Instrument>();
    instruments.add(drumKit);
    DlsBank bank = new DlsBank("self", "DLS ", 1, 0, 0, instruments, new ArrayList<Wave>());
    return bank.midiInstrument(120 << 7, 0) == drumKit
            && bank.midiInstrument(120 << 7, 24) == drumKit
            && bank.midiInstrument(121 << 7, 24) == null;
}

static boolean defaultModeBankSelectorSelfCheck() {
    Articulation articulation = new Articulation();
    Region melodic = new Region(false, articulation);
    melodic.tableIndex = 0;
    Region cc0Region = new Region(false, articulation);
    cc0Region.tableIndex = 1;
    List<Instrument> instruments = new ArrayList<Instrument>();
    instruments.add(new Instrument(121 << 8, 0, "DLS ", articulation, Collections.singletonList(melodic)));
    instruments.add(new Instrument((121 << 8) | 122, 0, true, articulation, Collections.singletonList(cc0Region)));
    List<Wave> waves = new ArrayList<Wave>();
    waves.add(new Wave(0, 1, 1, 22050, 16, 2, -1, new short[]{0, 0, 0}, new SampleInfo()));
    waves.add(new Wave(1, 1, 1, 22050, 16, 2, -1, new short[]{0, 0, 0}, new SampleInfo()));
    PreviewRenderer renderer = new PreviewRenderer(new DlsBank("self", "DLS ", 2, 0, 0, instruments, waves),
            22050, 1);
    renderer.noteOn(0, 60, 100);
    renderer.controller(renderer.channels[0], 0, 122);
    renderer.programChange(renderer.channels[0], 0);
    renderer.noteOn(0, 60, 100);
    return renderer.channels[0].bankSelector() == ((121 << 7) | 122)
            && renderer.voices.size() == 2 && renderer.voices.get(1).wave.index == 1;
}

static byte[] minimalInstrumentBytes(int rawBank, int rawInstrument, int declaredRegions) {
    ByteArrayOutputStream regionChunks = new ByteArrayOutputStream();
    ascii(regionChunks, "rgnh");
    le32(regionChunks, 12);
    le16(regionChunks, 0);
    le16(regionChunks, 127);
    le16(regionChunks, 0);
    le16(regionChunks, 127);
    le16(regionChunks, 0);
    le16(regionChunks, 0);
    ascii(regionChunks, "wlnk");
    le32(regionChunks, 12);
    le16(regionChunks, 0);
    le16(regionChunks, 0);
    le32(regionChunks, 0);
    le32(regionChunks, 0);

    ByteArrayOutputStream rgnList = new ByteArrayOutputStream();
    ascii(rgnList, "LIST");
    le32(rgnList, 4 + regionChunks.size());
    ascii(rgnList, "rgn ");
    rgnList.write(regionChunks.toByteArray(), 0, regionChunks.size());

    ByteArrayOutputStream lrgnBody = new ByteArrayOutputStream();
    ascii(lrgnBody, "lrgn");
    lrgnBody.write(rgnList.toByteArray(), 0, rgnList.size());

    ByteArrayOutputStream ins = new ByteArrayOutputStream();
    ascii(ins, "insh");
    le32(ins, 12);
    le32(ins, declaredRegions);
    le32(ins, rawBank);
    le32(ins, rawInstrument);
    ascii(ins, "LIST");
    le32(ins, lrgnBody.size());
    ins.write(lrgnBody.toByteArray(), 0, lrgnBody.size());
    return ins.toByteArray();
}

static boolean duplicateSelectorSelfCheck() {
    Articulation articulation = new Articulation();
    List<Region> regions = new ArrayList<Region>();
    regions.add(new Region(false, articulation));
    List<Instrument> instruments = new ArrayList<Instrument>();
    instruments.add(new Instrument(121 << 8, 0, "DLS ", articulation, regions));
    instruments.add(new Instrument(121 << 8, 0, "DLS ", articulation, regions));
    try {
        new DlsBank("self", "DLS ", 2, 0, 0, instruments, new ArrayList<Wave>());
        return false;
    } catch (IllegalArgumentException expected) {
        return true;
    }
}

static boolean articulationWhitelistSelfCheck() {
    ByteArrayOutputStream art = new ByteArrayOutputStream();
    le32(art, 8);
    le32(art, 1);
    le16(art, 0x7777);
    le16(art, 0);
    le16(art, 3);
    le16(art, 0);
    le32(art, 0);
    try {
        new DlsParser(art.toByteArray(), "art-whitelist-self")
                .parseArticulationChunk(0, art.size(), new Articulation());
        return false;
    } catch (IllegalArgumentException expected) {
        return true;
    }
}

static boolean observedSource102SelfCheck() {
    Articulation articulation = new Articulation();
    articulation.apply(new Connection(0x102, 0, 3, 0x4000, 6553600));
    int value = controllerConnectionValueQ16(new Connection(0x102, 0, 3, 0x4000, 6553600),
            8192, 0, 100 << 7, 127 << 7, 64 << 7, 0, 0,
            0x0100, 0x2000, 0x3FFF);
    return articulation.connectionCount == 1 && articulation.runtimeConnections.isEmpty() && value == 0;
}

static boolean baseNoEffectDestinationSelfCheck() {
    int[][] cases = {
            {0x114, -66650032},
            {0x115, 12345},
            {0x500, 571867136},
            {0x501, 3656908}
    };
    for (int[] item : cases) {
        ByteArrayOutputStream art = new ByteArrayOutputStream();
        le32(art, 8);
        le32(art, 1);
        le16(art, 0);
        le16(art, 0);
        le16(art, item[0]);
        le16(art, 0);
        le32(art, item[1]);
        Articulation articulation = new Articulation();
        try {
            new DlsParser(art.toByteArray(), "no-effect-dst-self")
                    .parseArticulationChunk(0, art.size(), articulation);
        } catch (IllegalArgumentException expected) {
            return false;
        }
        if (articulation.runtimeConnections.size() != 0 || articulation.pitch != 0
                || articulation.lfoFrequency != 200000 || articulation.lfoStartDelay != 10000) {
            return false;
        }
    }
    return true;
}

static boolean cdlSelfCheck() {
    byte[] falseCdl = cdlConstChunk(0);
    byte[] trueCdl = cdlConstChunk(1);
    if (new DlsParser(falseCdl, "cdl-false-self").cdlPasses(0, falseCdl.length)
            || !new DlsParser(trueCdl, "cdl-true-self").cdlPasses(0, trueCdl.length)) {
        return false;
    }

    ByteArrayOutputStream notBody = new ByteArrayOutputStream();
    le16(notBody, 0x10);
    le32(notBody, 0);
    le16(notBody, 0x0F);
    byte[] notCdl = cdlChunk(notBody.toByteArray());
    if (!new DlsParser(notCdl, "cdl-not-self").cdlPasses(0, notCdl.length)) {
        return false;
    }

    ByteArrayOutputStream queryBody = new ByteArrayOutputStream();
    le16(queryBody, 0x11);
    int[] supportsDls2 = {
            0x27, 0x2F, 0x8F, 0x17, 0x64, 0xC3, 0xD1, 0x11,
            0xA7, 0x60, 0x00, 0x00, 0xF8, 0x75, 0xAC, 0x12
    };
    for (int b : supportsDls2) {
        queryBody.write(b);
    }
    byte[] queryCdl = cdlChunk(queryBody.toByteArray());
    if (!new DlsParser(queryCdl, "cdl-query-self").cdlPasses(0, queryCdl.length)) {
        return false;
    }

    ByteArrayOutputStream mathBody = new ByteArrayOutputStream();
    le16(mathBody, 0x10);
    le32(mathBody, 7);
    le16(mathBody, 0x04);
    byte[] math = mathBody.toByteArray();
    if (new DlsParser(math, "cdl-math-self").evalCdl(0, math.length) != 14) {
        return false;
    }

    ByteArrayOutputStream supportedBody = new ByteArrayOutputStream();
    le16(supportedBody, 0x12);
    for (int b : DlsParser.CDL_QUERY_GUIDS[0]) {
        supportedBody.write(b);
    }
    byte[] supported = supportedBody.toByteArray();
    if (new DlsParser(supported, "cdl-supported-self").evalCdl(0, supported.length) != 0) {
        return false;
    }

    ByteArrayOutputStream unknownBody = new ByteArrayOutputStream();
    le16(unknownBody, 0x10);
    le32(unknownBody, 9);
    le16(unknownBody, 0x11);
    for (int i = 0; i < 16; i++) {
        unknownBody.write(0xFF);
    }
    byte[] unknown = unknownBody.toByteArray();
    if (new DlsParser(unknown, "cdl-unknown-self").evalCdl(0, unknown.length) != 9) {
        return false;
    }

    ByteArrayOutputStream rootBody = new ByteArrayOutputStream();
    ascii(rootBody, "DLS ");
    rootBody.write(falseCdl, 0, falseCdl.length);
    ByteArrayOutputStream riff = new ByteArrayOutputStream();
    ascii(riff, "RIFF");
    le32(riff, rootBody.size());
    riff.write(rootBody.toByteArray(), 0, rootBody.size());
    try {
        new DlsParser(riff.toByteArray(), "cdl-root-self").parseRoot();
        return false;
    } catch (IllegalArgumentException expected) {
        // Top-level cdl false rejects the whole collection.
    }

    ByteArrayOutputStream regionBody = new ByteArrayOutputStream();
    regionBody.write(falseCdl, 0, falseCdl.length);
    ascii(regionBody, "rgnh");
    le32(regionBody, 12);
    le16(regionBody, 0);
    le16(regionBody, 127);
    le16(regionBody, 0);
    le16(regionBody, 127);
    le16(regionBody, 0);
    le16(regionBody, 0);
    ascii(regionBody, "wlnk");
    le32(regionBody, 12);
    le16(regionBody, 0);
    le16(regionBody, 0);
    le32(regionBody, 0);
    le32(regionBody, 0);
    ByteArrayOutputStream regionList = new ByteArrayOutputStream();
    ascii(regionList, "LIST");
    le32(regionList, 4 + regionBody.size());
    ascii(regionList, "rgn ");
    regionList.write(regionBody.toByteArray(), 0, regionBody.size());
    List<Region> regions = new ArrayList<Region>();
    new DlsParser(regionList.toByteArray(), "cdl-region-self")
            .parseRegions(0, regionList.size(), new Articulation(), regions);
    if (!regions.isEmpty()) {
        return false;
    }

    ByteArrayOutputStream artList = new ByteArrayOutputStream();
    artList.write(falseCdl, 0, falseCdl.length);
    ascii(artList, "art1");
    le32(artList, 20);
    le32(artList, 8);
    le32(artList, 1);
    le16(artList, 0);
    le16(artList, 0);
    le16(artList, 3);
    le16(artList, 0);
    le32(artList, 0);
    Articulation articulation = new Articulation();
    DlsParser artParser = new DlsParser(artList.toByteArray(), "cdl-art-self");
    artParser.parseArticulationList(0, artList.size(), articulation);
    if (articulation.connectionCount != 0 || artParser.articulationChunkCount != 0) {
        return false;
    }

    ByteArrayOutputStream regionArtBody = new ByteArrayOutputStream();
    ascii(regionArtBody, "rgnh");
    le32(regionArtBody, 12);
    le16(regionArtBody, 0);
    le16(regionArtBody, 127);
    le16(regionArtBody, 0);
    le16(regionArtBody, 127);
    le16(regionArtBody, 0);
    le16(regionArtBody, 0);
    ascii(regionArtBody, "wlnk");
    le32(regionArtBody, 12);
    le16(regionArtBody, 0);
    le16(regionArtBody, 0);
    le32(regionArtBody, 0);
    le32(regionArtBody, 0);
    ByteArrayOutputStream lartBody = new ByteArrayOutputStream();
    ascii(lartBody, "lart");
    lartBody.write(artList.toByteArray(), 0, artList.size());
    ascii(regionArtBody, "LIST");
    le32(regionArtBody, lartBody.size());
    regionArtBody.write(lartBody.toByteArray(), 0, lartBody.size());
    Articulation inherited = new Articulation();
    inherited.apply(new Connection(0, 0, 3, 0, 123400));
    Region region = new DlsParser(regionArtBody.toByteArray(), "cdl-region-art-self")
            .parseRegion(0, regionArtBody.size(), false, inherited);
    return region.articulation == inherited && inherited.pitch == 1234;
}

static byte[] cdlConstChunk(int value) {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    le16(body, 0x10);
    le32(body, value);
    return cdlChunk(body.toByteArray());
}

static byte[] cdlChunk(byte[] body) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ascii(out, "cdl ");
    le32(out, body.length);
    out.write(body, 0, body.length);
    return out.toByteArray();
}

static boolean chorusWetSelfCheck() {
    int[] bus = new int[4096];
    Arrays.fill(bus, 1000);
    int[] mix = new int[bus.length * 2];
    new ChorusEffect(22050).process(bus, mix, 0, bus.length);
    for (int value : mix) {
        if (value != 0) {
            return true;
        }
    }
    return false;
}

static boolean reverbWetSelfCheck() {
    int[] bus = new int[12000];
    Arrays.fill(bus, 1 << 20);
    int[] mix = new int[bus.length * 2];
    new ReverbEffect(22050).process(bus, mix, 0, bus.length);
    for (int value : mix) {
        if (value != 0) {
            return true;
        }
    }
    return false;
}
}
