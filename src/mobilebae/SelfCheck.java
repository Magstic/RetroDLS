package mobilebae;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

public final class SelfCheck {
    private SelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Paths.get(".") : Paths.get(args[0]);
        Path testDir = root.resolve("\u6e2c\u8a66\u7528\u7684dls\u548cmid");
        if (!Files.exists(testDir)) {
            testDir = root.resolve("TEST");
        }
        checkDls(root.resolve("Profiles").resolve("mobile30.dls"), 22, 71, 44, 44, 0, 36, 289);
        checkDls(root.resolve("Profiles").resolve("mobile60.dls"), 43, 125, 49, 49, 0, 58, 495);
        checkDls(root.resolve("MSB1.2").resolve("SampleContent").resolve("my_dls.dls"), 1, 3, 1, 1, 0, 1, 0);
        checkDls(root.resolve("MSB1.2").resolve("SampleContent").resolve("my_Mobile-DLS.dls"), 1, 3, 1, 1, 0, 1, 0);
        checkDls(testDir.resolve("Lloyd Bank.dls"), 41, 114, 55, 6, 49, 99, 712);

        MidiSong ending = MobileBae.loadMidi(testDir.resolve("ending.mid"));
        require(ending.division > 0, "ending.mid division");
        require(ending.events.size() > 0, "ending.mid events");
        require(ending.countStatus(0x90) > 0, "ending.mid note-on events");
        require(MobileBae.defaultMaxSeconds(ending) > 60
                        && MobileBae.defaultMaxSeconds(ending) * 1000000L > ending.lengthMicros,
                "cli default max seconds covers full midi");
        checkTargetMidiOracle(testDir);
        require(MobileBae.pitchRatioQ16(0) == 65536, "pitch ratio unity");
        require(MobileBae.pitchRatioQ16(1 << 16) == 69432, "pitch ratio semitone table");
        require(MobileBae.pitchRatioQ16(12 << 16) == 131072, "pitch ratio octave");
        require(MobileBae.exp2Q16(0) == 65536, "exp2 unity");
        require(MobileBae.exp2Q16(1 << 16) == 131072, "exp2 octave");
        require(MobileBae.exp10Q16(0) == 65536, "exp10 unity");
        require(MobileBae.exp10Q16(1 << 16) == 655360, "exp10 decade");
        require(MobileBae.log10Q16(0x10000) == 0, "log10 table unity");
        require(MobileBae.log10Q16(0xA0000) == 0x10000, "log10 table decade");
        require(MobileBae.transformSourceQ16(0x8000, 0x8400) < 0x8000,
                "concave inverted transform");
        require(MobileBae.finalMixSample(8388607) == 32767
                && MobileBae.finalMixSample(-8388608) == -32768
                && MobileBae.finalMixSample(9000000) == 32767,
                "final mix copy clamp");
        Connection keyPitch = new Connection(3, 0, 3, 0, 838860800);
        require(MobileBae.noteOnConnectionValueQ16(keyPitch, 72, 100, 60, 0, 0x0100) / 100 == (12 << 16),
                "keynumber pitch modulation");
        Connection keyDecay = new Connection(3, 0, 0x207, 0, -250019840);
        int decayValue = MobileBae.noteOnConnectionValueQ16(keyDecay, 72, 100, 60, 0, 0x0100);
        require(decayValue < 0, "keynumber decay modulation sign");
        require(MobileBae.modulatedTimeMicros(1000000, decayValue) < 1000000,
                "keynumber decay modulation effect");
        Connection velocityGain = new Connection(2, 0, 1, 0x8400, -31457280);
        require(MobileBae.noteOnConnectionValueQ16(velocityGain, 60, 0, 60, 0, 0x0100) < 0
                        && MobileBae.noteOnConnectionValueQ16(velocityGain, 60, 64, 60, 0, 0x0100)
                        < MobileBae.noteOnConnectionValueQ16(velocityGain, 60, 127, 60, 0, 0x0100)
                        && MobileBae.noteOnConnectionValueQ16(velocityGain, 60, 127, 60, 0, 0x0100) == 0,
                "note-on velocity gain transform");
        Connection pitchWheel = new Connection(6, 0x100, 3, 0x4000, 838860800);
        require(Math.abs(MobileBae.controllerConnectionValueQ16(pitchWheel, 8192,
                0, 100 << 7, 127 << 7, 64 << 7, 0, 0, 0x0100, 0x2000, 0x2000)) < 200,
                "pitch wheel center");
        require(MobileBae.controllerConnectionValueQ16(pitchWheel, 0x3FFF,
                0, 100 << 7, 127 << 7, 64 << 7, 0, 0, 0x0100, 0x2000, 0x2000) > 0,
                "pitch wheel positive");
        int defaultWheel = MobileBae.controllerConnectionValueQ16(pitchWheel, 0x3FFF,
                0, 100 << 7, 127 << 7, 64 << 7, 0, 0, 0x0100, 0x2000, 0x2000);
        int wideWheel = MobileBae.controllerConnectionValueQ16(pitchWheel, 0x3FFF,
                0, 100 << 7, 127 << 7, 64 << 7, 0, 0, 0x0200, 0x2000, 0x2000);
        require(wideWheel > defaultWheel * 19 / 10, "rpn0 pitch wheel range");
        Connection cc1 = new Connection(0x81, 0, 3, 0x4000, 6553600);
        require(MobileBae.controllerConnectionValueQ16(cc1, 8192,
                0x3FFF, 100 << 7, 127 << 7, 64 << 7, 0, 0, 0x0100, 0x2000, 0x2000) > 0,
                "cc1 modulation wheel source");
        Connection rpn1Fine = new Connection(0x101, 0, 3, 0x4000, 6553600);
        require(Math.abs(MobileBae.controllerConnectionValueQ16(rpn1Fine, 8192,
                0, 100 << 7, 127 << 7, 64 << 7, 0, 0, 0x0100, 0x2000, 0x2000)) < 1000,
                "rpn1 center");
        require(MobileBae.controllerConnectionValueQ16(rpn1Fine, 8192,
                0, 100 << 7, 127 << 7, 64 << 7, 0, 0, 0x0100, 0x3FFF, 0x2000) > 0,
                "rpn1 positive source");
        Connection cc7 = new Connection(0x87, 0, 1, 0x8400, -62914560);
        int cc7MsbOnly = MobileBae.controllerConnectionValueQ16(cc7, 8192,
                0, 64 << 7, 127 << 7, 64 << 7, 0, 0, 0x0100, 0x2000, 0x2000);
        int cc7WithLsb = MobileBae.controllerConnectionValueQ16(cc7, 8192,
                0, (64 << 7) | 127, 127 << 7, 64 << 7, 0, 0, 0x0100, 0x2000, 0x2000);
        require(cc7MsbOnly < 0,
                "cc7 attenuation");
        require(cc7WithLsb != cc7MsbOnly, "cc7 lsb affects attenuation");
        Connection cc10 = new Connection(0x8A, 0, 4, 0x4000, 33292288);
        require(MobileBae.controllerConnectionValueQ16(cc10, 8192,
                0, 100 << 7, 127 << 7, 64 << 7, 0, 0, 0x0100, 0x2000, 0x2000) == 0,
                "cc10 pan center");
        require(MobileBae.controllerConnectionValueQ16(cc10, 8192,
                0, 100 << 7, 127 << 7, 0x3FFF, 0, 0, 0x0100, 0x2000, 0x2000) > 0,
                "cc10 pan");
        Connection cc91 = new Connection(0xDB, 0, 0x81, 0, 65536000);
        require(MobileBae.controllerConnectionValueQ16(cc91, 8192,
                0, 100 << 7, 127 << 7, 64 << 7, 40, 0, 0x0100, 0x2000, 0x2000) == 20480000,
                "cc91 reverb source");
        require(MobileBae.sustainGateSelfCheck(), "sustain gate release");
        require(MobileBae.rpnPitchRangeSelfCheck(), "rpn pitch range controller path");
        require(MobileBae.lfoEg2SourceSelfCheck(), "lfo and eg2 runtime sources");
        require(MobileBae.voiceControlQuantumSelfCheck(), "voice control block quantum");
        require(MobileBae.eg1MultiplierSelfCheck(), "eg1 multiplier and sustain mapping");
        require(MobileBae.gainRampSelfCheck(), "voice gain ramp");
        require(MobileBae.panAccumulatorSelfCheck(), "pan accumulator source");
        require(MobileBae.resetControllersSelfCheck(), "reset controllers preserves program bank");
        require(MobileBae.footControllerSelfCheck(), "foot controller state");
        require(MobileBae.bankSelectResetSelfCheck(), "bank select msb clears lsb");
        require(MobileBae.nrpnSelectorQuirkSelfCheck(), "nrpn selector quirk");
        require(MobileBae.effectSendSelfCheck(), "reverb and chorus send bus");
        require(MobileBae.effectGateSelfCheck(), "effect state gate and tail");
        require(MobileBae.mixDynamicsSelfCheck(), "mix dynamics gain ramp");
        require(MobileBae.mixDynamicsSongEndSelfCheck(), "mix dynamics song-end gate");
        require(MobileBae.streamChunkingSelfCheck(), "stream block ring chunking");
        require(MobileBae.stereoSourceSelfCheck(), "stereo source dry matrix");
        require(MobileBae.allNotesControllerSelfCheck(), "all-notes/all-sound controller release");
        require(MobileBae.mipSelfCheck(), "sp-midi mip note gate");
        require(MobileBae.globalSysExSelfCheck(), "global sysex volume and tuning");
        require(MobileBae.systemModeSysExSelfCheck(), "system mode sysex clears voices");
        require(MobileBae.voiceLimitSelfCheck(), "ordinary voice limit");
        require(MobileBae.vibrationFilterSelfCheck(), "program 124 vibration filter");
        require(MobileBae.exclusiveVoiceSelfCheck(), "exclusive voice release");
        require(MobileBae.sampleAttenuationSelfCheck(), "sample attenuation gain");
        require(MobileBae.sampleGuardFrameSelfCheck(), "sample guard frame");
        require(MobileBae.sourceInterpolationSelfCheck(), "source interpolation");
        require(MobileBae.loopWrapSelfCheck(), "loop wrap");
        require(MobileBae.noteOnPitchStepSelfCheck(), "note-on pitch step rounding");
        require(MobileBae.programAliasSelfCheck(), "pgal program alias");
        require(MobileBae.sourceIncrementClampSelfCheck(), "source increment clamp");
        require(MobileBae.instChunkSelfCheck(), "inst chunk sample info");
        require(MobileBae.waveCompletionSelfCheck(), "wave completion fact gate");
        require(MobileBae.imaWavSelfCheck(), "ima wav decoder");
        require(MobileBae.midiDataMaskSelfCheck(), "midi data byte mask");
        require(MobileBae.midiSystemEventSelfCheck(), "midi system event gate");
        require(MobileBae.midiHeaderGateSelfCheck(), "midi header gate");
        require(MobileBae.midiFinalDeltaClampSelfCheck(), "midi final delta clamp");
        require(MobileBae.midiRuntimeTimingSelfCheck(), "midi runtime timing");
        require(MobileBae.midiMetaLengthSelfCheck(), "midi meta length gate");
        require(MobileBae.midiChunkSkipSelfCheck(), "midi chunk skip");
        require(MobileBae.instrumentRegionCountSelfCheck(), "instrument region count gate");
        require(MobileBae.selectorModeSelfCheck(), "dls selector mode");
        require(MobileBae.drumProgramResourceLookupSelfCheck(), "drum program resource lookup");
        require(MobileBae.defaultModeBankSelectorSelfCheck(), "default mode bank selector routing");
        require(MobileBae.duplicateSelectorSelfCheck(), "duplicate selector gate");
        require(MobileBae.articulationWhitelistSelfCheck(), "articulation whitelist gate");
        require(MobileBae.observedSource102SelfCheck(), "observed source 0x102 routing");
        require(MobileBae.baseNoEffectDestinationSelfCheck(), "base no-effect destination routing");
        require(MobileBae.cdlSelfCheck(), "dls cdl condition gate");
        require(MobileBae.chorusWetSelfCheck(), "chorus wet output");
        require(MobileBae.reverbWetSelfCheck(), "reverb wet output");
        DlsBank lloydBank = MobileBae.loadDls(testDir.resolve("Lloyd Bank.dls"));
        require(lloydBank.nonZeroAttenuationCount() > 0, "lloyd attenuation coverage");
        require(lloydBank.programAliasFor(81) == 90, "lloyd pgal program alias");
        require(MobileBae.playableNoteOnCount(lloydBank, ending) == ending.realNoteOnCount(),
                "lloyd pgal covers ending.mid programs");
        Path lloydDrumMidi = Files.createTempFile("mobilebae-lloyd-drum-key60-", ".mid");
        try {
            Files.write(lloydDrumMidi, new byte[]{
                    0x4D, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 0, 0, 1, 1, (byte) 0xE0,
                    0x4D, 0x54, 0x72, 0x6B, 0, 0, 0, 0x18,
                    0, (byte) 0xFF, 0x51, 3, 7, (byte) 0xA1, 0x20,
                    0, (byte) 0xB9, 0x5B, 0x28,
                    0, (byte) 0x99, 0x3C, 0x64,
                    (byte) 0x87, 0x40, (byte) 0x89, 0x3C, 0,
                    0, (byte) 0xFF, 0x2F, 0
            });
            MidiSong lloydDrumSong = MobileBae.loadMidi(lloydDrumMidi);
            short[] lloydDry = MobileBae.renderPreview(lloydBank, lloydDrumSong, 44100, 2, false);
            require(channelWindowStats(lloydDry, 2646, 441, 0)
                            .equals("440:0:-737:745:99E239CDB760CDA8EC44C35D25BEF9921C2C9E070639B25B5ED76CE1D46BAE15"),
                    "lloyd A-law percussion dry left dll oracle window");
            require(channelWindowStats(lloydDry, 2646, 441, 1)
                            .equals("441:0:-1779:1798:A142A6BCC035AA41760BE80174B10198A11D2D3966D58EE76EDE48E731BE656C"),
                    "lloyd A-law percussion dry right dll oracle window");
        } finally {
            Files.deleteIfExists(lloydDrumMidi);
        }
        Path lloydDrumKey35Midi = Files.createTempFile("mobilebae-lloyd-drum-key35-", ".mid");
        try {
            Files.write(lloydDrumKey35Midi, new byte[]{
                    0x4D, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 0, 0, 1, 1, (byte) 0xE0,
                    0x4D, 0x54, 0x72, 0x6B, 0, 0, 0, 0x18,
                    0, (byte) 0xFF, 0x51, 3, 7, (byte) 0xA1, 0x20,
                    0, (byte) 0xB9, 0x5B, 0x28,
                    0, (byte) 0x99, 0x23, 0x64,
                    (byte) 0x87, 0x40, (byte) 0x89, 0x23, 0,
                    0, (byte) 0xFF, 0x2F, 0
            });
            MidiSong lloydDrumKey35Song = MobileBae.loadMidi(lloydDrumKey35Midi);
            short[] lloydKey35Dry = MobileBae.renderPreview(lloydBank, lloydDrumKey35Song, 44100, 2, false);
            require(channelWindowStats(lloydKey35Dry, 2646, 441, 0)
                            .equals("441:0:-2630:2462:EC0F0BB293EF1328211B64C543DE43B0C545E8159D1F9EC070AE00E55D57C687"),
                    "lloyd A-law key35 window-boundary dry left dll oracle window");
            require(channelWindowStats(lloydKey35Dry, 2646, 441, 1)
                            .equals("441:0:-2630:2462:9B78353AC706665288E4E887FCBFD535CD788F1B8F2924892043A73AF5B25314"),
                    "lloyd A-law key35 window-boundary dry right dll oracle window");
        } finally {
            Files.deleteIfExists(lloydDrumKey35Midi);
        }

        DlsBank sampleBank = MobileBae.loadDls(root.resolve("MSB1.2").resolve("SampleContent").resolve("my_dls.dls"));
        Instrument sampleInstrument = sampleBank.instruments.get(0);
        require(sampleInstrument.bankMsb == 121 && sampleInstrument.bankLsb == 2 && sampleInstrument.program == 0,
                "source dls implicit custom bank selector");
        Articulation sampleArticulation = sampleBank.instruments.get(0).articulation;
        require(sampleArticulation.runtimeConnections.size() == 9, "default articulation connection count");
        require(hasConnection(sampleArticulation, 3, 0, 3), "default keynumber pitch");
        require(hasConnection(sampleArticulation, 2, 0, 1), "default velocity gain");
        MidiSong sampleMidi = MobileBae.loadMidi(root.resolve("MSB1.2").resolve("SampleContent").resolve("my_mid.mid"));
        require(MobileBae.songChildTailInput(sampleMidi) == 6, "official sample child tail estimator");
        short[] samplePreview = MobileBae.renderPreview(sampleBank, sampleMidi, 44100, 5);
        int oracleStartFrame = 44100 * 4150 / 1000;
        require(channelWindowStats(samplePreview, oracleStartFrame, 441, 0)
                        .equals("441:0:-6756:6816:AB622F504F2D53582462CD8053410AE02594A2592858539BC8C09FFEC0042DFD"),
                "official sample java left oracle window");
        require(channelWindowStats(samplePreview, oracleStartFrame, 441, 1)
                        .equals("441:0:-6725:6679:8F1EC80DB4AF7AB8D34F4DF2C775F5909B983306458BCE3953219ADAE220DCC1"),
                "official sample java right oracle window");
        short[] sampleDryPreview = MobileBae.renderPreview(sampleBank, sampleMidi, 44100, 5, false);
        int dryOracleStartFrame = 44100 * 4300 / 1000;
        require(channelWindowStats(sampleDryPreview, dryOracleStartFrame, 441, 0)
                        .equals("436:0:-2465:2695:2F6CFE384DE3217EB83E0807B200CBA75856B0023F01F797462E53A36BD36C97"),
                "official sample java dry left dll oracle window");
        require(channelWindowStats(sampleDryPreview, dryOracleStartFrame, 441, 1)
                        .equals("436:0:-2465:2695:2F6CFE384DE3217EB83E0807B200CBA75856B0023F01F797462E53A36BD36C97"),
                "official sample java dry right dll oracle window");

        DlsBank mobile60Bank = MobileBae.loadDls(root.resolve("Profiles").resolve("mobile60.dls"));
        Region mobile60Program7Region = mobile60Bank.instrumentFor(121, 0, 7).regionFor(64, 100);
        require(mobile60Program7Region.articulation.filterCutoff == 6800015
                        && mobile60Program7Region.articulation.filterResonance == 364380,
                "mobile60 plus filter static articulation");
        Path mobile60Midi = Files.createTempFile("mobilebae-mobile60-prog48-", ".mid");
        try {
            Files.write(mobile60Midi, new byte[]{
                    0x4D, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 0, 0, 1, 1, (byte) 0xE0,
                    0x4D, 0x54, 0x72, 0x6B, 0, 0, 0, 0x23, 0, (byte) 0xFF, 0x51, 3, 7, (byte) 0xA1, 0x20,
                    0, (byte) 0xB0, 0, 0x79, 0, (byte) 0xB0, 0x20, 0, 0, (byte) 0xC0, 0x30,
                    0, (byte) 0xB0, 0x5B, 0x28, 0, (byte) 0x90, 0x3C, 0x64,
                    (byte) 0x87, 0x40, (byte) 0x80, 0x3C, 0, 0, (byte) 0xFF, 0x2F, 0
            });
            MidiSong mobile60Song = MobileBae.loadMidi(mobile60Midi);
            short[] mobile60Dry = MobileBae.renderPreview(mobile60Bank, mobile60Song, 44100, 2, false);
            int mobile60OracleStart = 44100;
            require(channelWindowStats(mobile60Dry, mobile60OracleStart, 441, 0)
                            .equals("441:0:-11031:12596:223638AB7B855BB08E3D056B7B846A3E1F2665EAF1BAC22206BA13DFAFBEA1B5"),
                    "mobile60 16-bit dry left dll oracle window");
            require(channelWindowStats(mobile60Dry, mobile60OracleStart, 441, 1)
                            .equals("441:0:-11031:12596:223638AB7B855BB08E3D056B7B846A3E1F2665EAF1BAC22206BA13DFAFBEA1B5"),
                    "mobile60 16-bit dry right dll oracle window");
        } finally {
            Files.deleteIfExists(mobile60Midi);
        }
        Path mobile60Program7Midi = Files.createTempFile("mobilebae-mobile60-prog7-", ".mid");
        try {
            Files.write(mobile60Program7Midi, new byte[]{
                    0x4D, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 0, 0, 1, 1, (byte) 0xE0,
                    0x4D, 0x54, 0x72, 0x6B, 0, 0, 0, 0x23, 0, (byte) 0xFF, 0x51, 3, 7, (byte) 0xA1, 0x20,
                    0, (byte) 0xB0, 0, 0x79, 0, (byte) 0xB0, 0x20, 0, 0, (byte) 0xC0, 7,
                    0, (byte) 0xB0, 0x5B, 0x28, 0, (byte) 0x90, 0x40, 0x64,
                    (byte) 0x87, 0x40, (byte) 0x80, 0x40, 0, 0, (byte) 0xFF, 0x2F, 0
            });
            MidiSong mobile60Program7Song = MobileBae.loadMidi(mobile60Program7Midi);
            short[] mobile60Program7Dry = MobileBae.renderPreview(mobile60Bank, mobile60Program7Song, 44100, 2, false);
            require(channelWindowStats(mobile60Program7Dry, 44100, 441, 0)
                            .equals("440:0:-1620:1412:D51760DAC06DC4E186D037D0FFF64D7506126674762413946A23C1A2D25BB463"),
                    "mobile60 plus filtered dry left dll oracle window");
            require(channelWindowStats(mobile60Program7Dry, 44100, 441, 1)
                            .equals("440:0:-1620:1412:D51760DAC06DC4E186D037D0FFF64D7506126674762413946A23C1A2D25BB463"),
                    "mobile60 plus filtered dry right dll oracle window");
        } finally {
            Files.deleteIfExists(mobile60Program7Midi);
        }
        Path mobile60Program73Midi = Files.createTempFile("mobilebae-mobile60-prog73-", ".mid");
        try {
            Files.write(mobile60Program73Midi, new byte[]{
                    0x4D, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 0, 0, 1, 1, (byte) 0xE0,
                    0x4D, 0x54, 0x72, 0x6B, 0, 0, 0, 0x23, 0, (byte) 0xFF, 0x51, 3, 7, (byte) 0xA1, 0x20,
                    0, (byte) 0xB0, 0, 0x79, 0, (byte) 0xB0, 0x20, 0, 0, (byte) 0xC0, 73,
                    0, (byte) 0xB0, 0x5B, 0x28, 0, (byte) 0x90, 0x3C, 0x64,
                    (byte) 0x87, 0x40, (byte) 0x80, 0x3C, 0, 0, (byte) 0xFF, 0x2F, 0
            });
            MidiSong mobile60Program73Song = MobileBae.loadMidi(mobile60Program73Midi);
            short[] mobile60Program73Dry = MobileBae.renderPreview(mobile60Bank, mobile60Program73Song, 44100, 2, false);
            require(channelWindowStats(mobile60Program73Dry, 44100, 441, 0)
                            .equals("441:0:-6564:7651:A30AC836529BA48F1A4EACF7EFDF34D6EA122443CC62C2A5C06F7E628F067241"),
                    "mobile60 plus vibrato 8-bit filtered dry left dll oracle window");
            require(channelWindowStats(mobile60Program73Dry, 44100, 441, 1)
                            .equals("441:0:-6564:7651:A30AC836529BA48F1A4EACF7EFDF34D6EA122443CC62C2A5C06F7E628F067241"),
                    "mobile60 plus vibrato 8-bit filtered dry right dll oracle window");
        } finally {
            Files.deleteIfExists(mobile60Program73Midi);
        }

        DlsBank bank = MobileBae.loadDls(root.resolve("Profiles").resolve("mobile30.dls"));
        short[] preview = MobileBae.renderPreview(bank, ending, 22050, 5);
        boolean nonZero = false;
        for (short sample : preview) {
            if (sample != 0) {
                nonZero = true;
                break;
            }
        }
        require(nonZero, "preview render produced silence");
        require(sha256Pcm(preview).equals("BA01B8E6D0AC24ED0EA4B39D8AE21DDEF704B055B6EC5157A6BABE0775DA66F0"),
                "preview pcm regression hash");

        byte[] wav = MobileBae.wavBytes(preview, 22050);
        require(wav.length == 44 + preview.length * 2, "preview wav length");
        Path tempWav = Files.createTempFile("mobilebae-selfcheck-", ".wav");
        try {
            MobileBae.main(new String[]{
                    root.resolve("Profiles").resolve("mobile30.dls").toString(),
                    testDir.resolve("ending.mid").toString(),
                    tempWav.toString(),
                    "22050",
                    "1",
                    "--polyphony",
                    "8"
            });
            require(Files.size(tempWav) > 44, "cli wav render");
        } finally {
            Files.deleteIfExists(tempWav);
        }
        System.out.println("OK DLS/MIDI parser and preview renderer smoke check passed");
    }

    private static void checkDls(Path path, int instruments, int regions, int waves, int pcm, int alaw,
                                 int artChunks, int artConnections) throws Exception {
        DlsBank bank = MobileBae.loadDls(path);
        require(bank.instruments.size() == instruments, path + " instruments " + bank.instruments.size());
        require(bank.regionCount() == regions, path + " regions " + bank.regionCount());
        require(bank.waves.size() == waves, path + " waves " + bank.waves.size());
        require(bank.waveCountByFormatTag(1) == pcm, path + " PCM waves " + bank.waveCountByFormatTag(1));
        require(bank.waveCountByFormatTag(6) == alaw, path + " A-law waves " + bank.waveCountByFormatTag(6));
        require(bank.articulationChunkCount == artChunks, path + " art chunks " + bank.articulationChunkCount);
        require(bank.articulationConnectionCount == artConnections,
                path + " art connections " + bank.articulationConnectionCount);
    }

    private static void checkTargetMidiOracle(Path testDir) throws Exception {
        String prefix = "\u52b2\u4e50\u4e89\u9738 BGM";
        if (!Files.exists(testDir.resolve(prefix + "01.midi"))) {
            return;
        }
        int[] dllLengthMs = new int[]{
                69799, 41380, 52167, 65999, 73933, 29800, 56128, 52843,
                71587, 55793, 89769, 64598, 84121, 60000, 42080, 65547,
                65624, 72935, 56195, 50999, 62535, 57447, 60647, 38873
        };
        int[] eventCounts = new int[]{
                579, 2325, 1665, 2042, 861, 2163, 1428, 1352,
                3619, 3194, 1818, 4187, 1882, 2297, 2759, 4885,
                2521, 4160, 5242, 4680, 5537, 3238, 4859, 2672
        };
        int[] noteCounts = new int[]{
                280, 1140, 809, 1015, 413, 1053, 675, 644,
                1376, 1590, 794, 1168, 916, 927, 1301, 2408,
                1027, 1759, 2607, 2316, 2123, 1581, 2419, 1321
        };
        for (int i = 0; i < dllLengthMs.length; i++) {
            Path midi = testDir.resolve(prefix + String.format("%02d", i + 1) + ".midi");
            require(Files.exists(midi), midi.getFileName() + " exists");
            MidiSong song = MobileBae.loadMidi(midi);
            require(Math.abs(song.lengthMicros / 1000L - dllLengthMs[i]) <= 1,
                    midi.getFileName() + " DLL length oracle");
            require(song.events.size() == eventCounts[i], midi.getFileName() + " event count");
            require(song.realNoteOnCount() == noteCounts[i], midi.getFileName() + " note count");
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private static boolean hasConnection(Articulation articulation, int source, int control, int destination) {
        for (Connection connection : articulation.runtimeConnections) {
            if (connection.source == source && connection.control == control && connection.destination == destination) {
                return true;
            }
        }
        return false;
    }

    private static String sha256Pcm(short[] pcm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (short sample : pcm) {
            digest.update((byte) sample);
            digest.update((byte) (sample >>> 8));
        }
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest.digest()) {
            out.append(String.format("%02X", b & 0xFF));
        }
        return out.toString();
    }

    private static String channelWindowStats(short[] stereoPcm, int startFrame, int frames, int channel) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        int nonZero = 0;
        int firstNonZero = -1;
        int min = 0;
        int max = 0;
        for (int i = 0; i < frames; i++) {
            short sample = stereoPcm[(startFrame + i) * 2 + channel];
            digest.update((byte) sample);
            digest.update((byte) (sample >>> 8));
            if (sample != 0) {
                if (firstNonZero < 0) {
                    firstNonZero = i;
                }
                nonZero++;
                if (sample < min) {
                    min = sample;
                }
                if (sample > max) {
                    max = sample;
                }
            }
        }
        StringBuilder hash = new StringBuilder(64);
        for (byte b : digest.digest()) {
            hash.append(String.format("%02X", b & 0xFF));
        }
        return nonZero + ":" + firstNonZero + ":" + min + ":" + max + ":" + hash;
    }
}
