package mobilebae;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fixed vectors derived from mBAE_base14.dll, not a MIDI song. */
public final class BaeSelectionSelfCheck {
    private static int passed;
    private BaeSelectionSelfCheck() { }

    private static void same(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
        passed++;
    }

    private static void same(String label, Instrument expected, Instrument actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
        passed++;
    }

    private static Instrument testInstrument(int msb, int lsb, int program) {
        return new Instrument((msb << 8) | lsb, program, true, new Articulation(),
                Collections.<Region>emptyList());
    }

    private static DlsBank bank(List<Instrument> ins, Map<Integer,Integer> aliases, int[] drumKeys) {
        return new DlsBank("synthetic", "DLSM", ins.size(), 0, 0, ins,
                new ArrayList<Wave>(), drumKeys, aliases);
    }

    private static int id(int msb, int lsb, int program) {
        return SynthesisSupport.selector(msb, lsb, program);
    }

    public static void main(String[] args) {
        final int melody = 121 << 7;
        final int drums = 120 << 7;
        // Native mode 0 (AUTO), 0x02266BB4..0x02266C0E.
        same("auto no CC melody", melody, BaeBankSelection.select(0,0,0,0));
        same("auto no CC drum", drums, BaeBankSelection.select(0,9,0,0));
        same("auto GS variation 1", melody | 1, BaeBankSelection.select(0,0,1,0));
        same("auto GS variation 2", melody | 2, BaeBankSelection.select(0,0,2,0));
        same("auto GS drum variation", drums | 3, BaeBankSelection.select(0,9,3,0));
        same("auto LSB-only variation", melody | 9, BaeBankSelection.select(0,0,0,9));
        same("auto two nonzero CC", melody, BaeBankSelection.select(0,0,4,9));
        same("auto two nonzero drum", drums, BaeBankSelection.select(0,9,4,9));
        same("auto GM2 melodic explicit", melody | 63, BaeBankSelection.select(0,9,121,63));
        same("auto GM2 drums explicit", drums | 63, BaeBankSelection.select(0,0,120,63));
        same("auto non-GM2 127 translated", melody | 127, BaeBankSelection.select(0,0,127,0));
        // Native mode 1/3, 0x02266B6D..0x02266B84.
        for (int mode : new int[]{1,3}) {
            for (int channel = 0; channel < 16; channel++) {
                same("fixed mode " + mode + " channel " + channel,
                        channel == 9 ? drums : melody,
                        BaeBankSelection.select(mode, channel, 121, 47));
            }
        }
        // Mode 2 (GM2), 0x02266C0F..0x02266C27.
        for (int msb : new int[]{0,1,64,119,120,121,126,127}) {
            for (int lsb : new int[]{0,1,9,10,127}) {
                same("GM2 explicit " + msb + "/" + lsb, (msb << 7)|lsb,
                        BaeBankSelection.select(2,9,msb,lsb));
            }
        }
        // Mode 4 (XG), full 128-entry jump table 0x02266C50.
        for (int msb=0;msb<128;msb++) {
            int expected = msb==126 || msb==127 ? drums : melody;
            same("XG table MSB " + msb, expected, BaeBankSelection.select(4,9,msb,54));
        }
        same("invalid internal mode", 0, BaeBankSelection.select(6,0,121,8));
        same("explicit private override", (54<<7)|13,
                BaeBankSelection.select(1,9,121,7,54,13));
        same("partial override invalid", melody,
                BaeBankSelection.select(0,0,0,0,54,0xFF));

        Instrument root = testInstrument(121,0,40);
        Instrument variation = testInstrument(121,2,40);
        Instrument drumKit = testInstrument(120,0,0);
        Instrument custom = testInstrument(54,1,7);
        Map<Integer,Integer> map = new HashMap<Integer,Integer>();
        map.put(id(121,0,48), id(121,0,40));
        map.put(id(121,3,48), id(121,0,48)); // missing alias target: retry base bank
        map.put(id(54,1,8), id(54,1,9));
        map.put(id(54,1,9), id(54,1,7)); // two-stage alias must not chain
        DlsBank test = bank(Arrays.asList(root, variation, drumKit, custom), map, null);
        same("exact variation first",variation,test.midiInstrument(melody|2,40));
        same("alias one hop",root,test.midiInstrument(melody,48));
        same("missing alias target falls to base",root,test.midiInstrument(melody|3,48));
        same("no alias chaining",(Instrument)null,test.midiInstrument((54<<7)|1,8));
        same("base bank fallback",root,test.midiInstrument(melody|5,40));
        same("variation to base alias",root,test.midiInstrument(melody|5,48));
        same("GM2 custom exact",custom,test.midiInstrument((54<<7)|1,7));
        same("do not coerce custom",(Instrument)null,test.midiInstrument((54<<7)|2,7));
        same("drum variation fallback",drumKit,test.midiInstrument(drums|5,26));
        same("drum program fallback",drumKit,test.midiInstrument(drums,26));
        same("no melodic missing fallback",(Instrument)null,test.midiInstrument(melody,97));

        int[] keyAliases = new int[128];
        for (int i=0;i<128;i++) keyAliases[i]=i;
        keyAliases[36]=38;
        keyAliases[37]=0x80;
        DlsBank keys = bank(Collections.singletonList(drumKit),null,keyAliases);
        same("drum key alias",38,keys.percussionKeyAliasFor(drums,36));
        same("unmapped key sentinel",0x80,keys.percussionKeyAliasFor(drums,37));
        same("melody skips drum alias",37,keys.percussionKeyAliasFor(melody,37));

        Instrument legacy = new Instrument(0x600,81,"DLS ",new Articulation(),Collections.<Region>emptyList());
        same("literal GS variation preserves bank MSB",6,legacy.bankMsb);
        same("literal GS variation preserves bank LSB",0,legacy.bankLsb);
        Instrument modern = new Instrument(0x790a,81,"DLS ",new Articulation(),Collections.<Region>emptyList());
        same("literal GM2 MSB",121,modern.bankMsb);
        same("literal GM2 LSB",10,modern.bankLsb);
        Instrument dlsm = new Instrument(0,81,"DLSM",new Articulation(),Collections.<Region>emptyList());
        same("DLSM does not become GM2",0,dlsm.bankMsb);

        Articulation articulation = new Articulation();
        DlsBank empty = bank(Collections.<Instrument>emptyList(),null,null);
        PreviewRenderer player = new PreviewRenderer(empty,22050,1);
        ChannelState ch=player.channels[0];
        player.controller(ch,0,2);
        player.controller(ch,32,9);
        same("independent LSB after CC32",9,ch.bankLsb);
        same("mixed controllers use base",melody,ch.bankSelector());
        player.controller(ch,0,1);
        same("native CC0 resets LSB",0,ch.bankLsb);
        same("native CC0 maps GS variation",melody|1,ch.bankSelector());
        ch.overrideBankMsb=77;
        ch.overrideBankLsb=65;
        same("channel override takes priority",(77<<7)|65,ch.bankSelector());
        player.controller(ch,32,5);
        same("CC32 clears private LSB override",melody,ch.bankSelector());
        ch.overrideBankMsb=77;
        ch.overrideBankLsb=65;
        player.controller(ch,0,3);
        same("CC0 clears private MSB override",melody|3,ch.bankSelector());
        System.out.println("OK " + passed + " BAE 1.2 selector and instrument-lookup regression checks passed");
    }
}
