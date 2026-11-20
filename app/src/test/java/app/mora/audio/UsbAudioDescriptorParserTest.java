package app.mora.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class UsbAudioDescriptorParserTest {
    @Test
    public void parsesUac1TypeOneFormatAndDiscreteRate() {
        byte[] raw = new byte[] {
            9, 4, 1, 1, 1, 1, 2, 0, 0,
            11, 0x24, 2, 1, 2, 2, 16, 1, (byte) 0x80, (byte) 0xBB, 0,
            7, 5, 1, 5, (byte) 0xC0, 0, 1
        };

        String report = UsbAudioDescriptorParser.parse(raw);

        assertTrue(report.contains("AudioStreaming: terdeteksi"));
        assertTrue(report.contains("channels=2"));
        assertTrue(report.contains("bitResolution=16 bit"));
        assertTrue(report.contains("48000 Hz"));
        assertTrue(report.contains("transfer=isochronous"));
    }

    @Test
    public void rejectsTruncatedDescriptorWithoutThrowing() {
        String report = UsbAudioDescriptorParser.parse(new byte[] { 9, 4, 1 });

        assertTrue(report.contains("malformed/truncated"));
        assertTrue(report.contains("Parsing dihentikan aman"));
    }

    @Test
    public void ignoresClockSourceWithoutReadableFrequencyControl() {
        byte[] raw = new byte[] {
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            8, 0x24, 0x0A, 7, 1, 0, 0, 0
        };

        List<UsbAudioDescriptorParser.ClockSource> clocks =
            UsbAudioDescriptorParser.findReadableClockSources(raw);

        assertTrue(clocks.isEmpty());
    }

    @Test
    public void discoversReadableUac2ClockSource() {
        byte[] raw = new byte[] {
            9, 4, 2, 0, 0, 1, 1, 0x20, 0,
            8, 0x24, 0x0A, 9, 1, 1, 0, 0
        };

        List<UsbAudioDescriptorParser.ClockSource> clocks =
            UsbAudioDescriptorParser.findReadableClockSources(raw);

        assertEquals(1, clocks.size());
        assertEquals(9, clocks.get(0).entityId);
        assertEquals(2, clocks.get(0).interfaceNumber);
        assertEquals(1, clocks.get(0).controlAccess);
    }

    @Test
    public void resolvesDirectUac2ClockFromStreamingTerminal() {
        byte[] raw = new byte[] {
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            17, 0x24, 0x02, 4, 1, 1, 0, 9, 2, 3, 0, 0, 0, 0, 0, 0, 0,
            8, 0x24, 0x0A, 9, 1, 1, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0x20, 0,
            16, 0x24, 0x01, 4, 0, 1, 0, 0, 0, 0, 2, 3, 0, 0, 0, 0
        };

        String topology = UsbAudioDescriptorParser.describeClockTopology(raw);

        assertTrue(topology.contains("terminalLink=4"));
        assertTrue(topology.contains("Clock Source 9"));
        assertTrue(topology.contains("frequency readable"));
    }

    @Test
    public void playbackClockFilteringReturnsOnlyReachableSource() {
        byte[] raw = new byte[] {
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            17, 0x24, 0x02, 4, 1, 1, 0, 9, 2, 3, 0, 0, 0, 0, 0, 0, 0,
            8, 0x24, 0x0A, 9, 1, 1, 0, 0,
            8, 0x24, 0x0A, 12, 1, 1, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0x20, 0,
            16, 0x24, 0x01, 4, 0, 1, 0, 0, 0, 0, 2, 3, 0, 0, 0, 0
        };

        List<UsbAudioDescriptorParser.ClockSource> clocks =
            UsbAudioDescriptorParser.findPlaybackClockSources(raw);

        assertEquals(1, clocks.size());
        assertEquals(9, clocks.get(0).entityId);
    }

    @Test
    public void playbackClockFilteringSkipsAmbiguousSelector() {
        byte[] raw = new byte[] {
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            17, 0x24, 0x02, 4, 1, 1, 0, 11, 2, 3, 0, 0, 0, 0, 0, 0, 0,
            9, 0x24, 0x0B, 11, 2, 9, 10, 1, 0,
            8, 0x24, 0x0A, 9, 1, 1, 0, 0,
            8, 0x24, 0x0A, 10, 1, 1, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0x20, 0,
            16, 0x24, 0x01, 4, 0, 1, 0, 0, 0, 0, 2, 3, 0, 0, 0, 0
        };

        assertTrue(UsbAudioDescriptorParser.findPlaybackClockSources(raw).isEmpty());
    }

    @Test
    public void followsClockMultiplierToItsSource() {
        byte[] raw = new byte[] {
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            17, 0x24, 0x02, 4, 1, 1, 0, 10, 2, 3, 0, 0, 0, 0, 0, 0, 0,
            7, 0x24, 0x0C, 10, 9, 1, 0,
            8, 0x24, 0x0A, 9, 1, 1, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0x20, 0,
            16, 0x24, 0x01, 4, 0, 1, 0, 0, 0, 0, 2, 3, 0, 0, 0, 0
        };

        String topology = UsbAudioDescriptorParser.describeClockTopology(raw);

        assertTrue(topology.contains("Clock Multiplier 10"));
        assertTrue(topology.contains("Clock Source 9"));
    }

    @Test
    public void doesNotGuessSelectedClockSelectorPin() {
        byte[] raw = new byte[] {
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            17, 0x24, 0x02, 4, 1, 1, 0, 11, 2, 3, 0, 0, 0, 0, 0, 0, 0,
            9, 0x24, 0x0B, 11, 2, 9, 10, 1, 0,
            8, 0x24, 0x0A, 9, 1, 1, 0, 0,
            8, 0x24, 0x0A, 10, 1, 1, 0, 0,
            9, 4, 1, 1, 1, 1, 2, 0x20, 0,
            16, 0x24, 0x01, 4, 0, 1, 0, 0, 0, 0, 2, 3, 0, 0, 0, 0
        };

        String topology = UsbAudioDescriptorParser.describeClockTopology(raw);

        assertTrue(topology.contains("Clock Selector 11"));
        assertTrue(topology.contains("AMBIGUOUS"));
        assertTrue(topology.contains("selected pin belum dibaca"));
    }

    @Test
    public void detectsClockEntityReferenceCycle() {
        byte[] raw = new byte[] {
            9, 4, 0, 0, 0, 1, 1, 0x20, 0,
            17, 0x24, 0x02, 4, 1, 1, 0, 10, 2, 3, 0, 0, 0, 0, 0, 0, 0,
            7, 0x24, 0x0C, 10, 11, 1, 0,
            7, 0x24, 0x0C, 11, 10, 1, 0,
            9, 4, 1, 1, 1, 1, 2, 0x20, 0,
            16, 0x24, 0x01, 4, 0, 1, 0, 0, 0, 0, 2, 3, 0, 0, 0, 0
        };

        assertTrue(UsbAudioDescriptorParser.describeClockTopology(raw).contains("cycle"));
    }

    @Test
    public void emptyInputIsReportedClearly() {
        assertEquals("Raw descriptor: kosong/tidak tersedia.",
            UsbAudioDescriptorParser.parse(new byte[0]));
        assertFalse(UsbAudioDescriptorParser.parse(null).isEmpty());
    }
}
