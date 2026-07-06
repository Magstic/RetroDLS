# Retro DLS

**Retro DLS** is an open Java SE DLS/MIDI renderer based on the [MobileBae](https://lpcwiki.miraheze.org/wiki/MobileBAE) behavior.

## Play

Java 8

### CLI Run

```sh
java -jar dist/retro-dls.jar bank.dls song.mid --play
```

```sh
java -jar dist/retro-dls.jar bank.dls song.mid output.wav
```

Optional:

```text
[sampleRate] [maxSeconds] [--polyphony voices] [--reverb|--no-reverb] [--chorus|--no-chorus] [--filter-vibration|--no-filter-vibration]
```

## Thanks

**ChatGPT**：Carried out 100% of the reverse engineering and 99% of the code cleanup.

**[Silent Talk](https://github.com/SmithGoll)**：Software testing, providing suggestions and assistance.

## Note

A clean-room reimplementation based on reverse-engineering of the MobileBAE.

This project has no affiliation with Beatnik.
