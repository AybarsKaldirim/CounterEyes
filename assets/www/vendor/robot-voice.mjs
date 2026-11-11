const clamp = (value, low, high) => Math.max(low, Math.min(high, value));
const vowels = /[aeıioöuüâîû]/;
const profiles = {
  welcome: { pitch: 285, pace: 1.05, lift: 1.28 },
  curious: { pitch: 250, pace: 0.92, lift: 1.55 },
  happy: { pitch: 325, pace: 1.16, lift: 1.35 },
  thoughtful: { pitch: 215, pace: 0.78, lift: 0.88 },
  sleepy: { pitch: 185, pace: 0.70, lift: 0.68 },
  review: { pitch: 270, pace: 0.94, lift: 1.15 },
};

export function splitSyllables(word) {
  const letters = Array.from(word);
  const positions = [];
  letters.forEach((letter, index) => { if (vowels.test(letter.toLocaleLowerCase('tr-TR'))) positions.push(index); });
  if (positions.length < 2) return [word];
  const parts = [];
  let start = 0;
  for (let index = 0; index < positions.length - 1; index++) {
    const boundary = Math.max(positions[index] + 1, positions[index + 1] - 1);
    parts.push(letters.slice(start, boundary).join(''));
    start = boundary;
  }
  parts.push(letters.slice(start).join(''));
  return parts.filter(Boolean);
}

export function buildMechanicalPlan(kind = 'static', random = Math.random, short = false) {
  const segments = [];
  if (kind === 'static') {
    const count = short ? 2 : 4;
    let at = 0;
    for (let index = 0; index < count; index++) {
      const duration = .025 + random() * .035;
      segments.push({
        kind: 'static', at, duration, pitch: 780, endPitch: 1100,
        formant: 850 + random() * 500, level: short ? .06 : .095,
        seed: Math.floor(random() * 0x7fffffff),
      });
      at += duration + .015 + random() * .035;
    }
  } else if (kind === 'handshake') {
    const notes = short ? [620, 830, 710] : [620, 830, 620, 910, 710, 830, 560, 740];
    let at = 0;
    for (let index = 0; index < notes.length; index++) {
      const duration = .035 + random() * .025;
      segments.push({
        kind: 'data', at, duration, pitch: notes[index], endPitch: notes[index],
        formant: 1550, level: .055,
      });
      at += duration + (index % 3 === 2 ? .075 : .012);
    }
    if (!short) {
      segments.push({
        kind: 'static', at: at + .025, duration: .10, pitch: 780, endPitch: 1000,
        formant: 1200, level: .04, seed: Math.floor(random() * 0x7fffffff),
      });
      segments.push({
        kind: 'rounded', at: at + .19, duration: .15,
        pitch: 390, endPitch: 560, formant: 900, level: .12,
      });
    }
  } else {
    segments.push({
      kind: 'motor', at: 0, duration: short ? .15 : .34,
      pitch: 100 + random() * 30, endPitch: 190, formant: 520, level: .09,
    });
    segments.push({
      kind: 'static', at: .025, duration: short ? .06 : .12, pitch: 300, endPitch: 500,
      formant: 430, level: .035, seed: Math.floor(random() * 0x7fffffff),
    });
    if (!short) {
      segments.push({
        kind: 'motor', at: .43, duration: .20, pitch: 170, endPitch: 85,
        formant: 450, level: .07,
      });
    }
  }
  const duration = Math.max(...segments.map(segment => segment.at + segment.duration)) + .045;
  return { text: '', intent: kind, captions: [], segments, duration };
}

function appendMechanicalAccent(segments, at, kind, random) {
  const accent = buildMechanicalPlan(kind, random, true);
  for (const segment of accent.segments) segments.push({ ...segment, at: segment.at + at });
  return accent.duration;
}

function noiseSegment(context, destination, segment, at) {
  const sampleCount = Math.max(1, Math.ceil(context.sampleRate * segment.duration));
  const buffer = context.createBuffer(1, sampleCount, context.sampleRate);
  const samples = buffer.getChannelData(0);
  let seed = segment.seed || 13579;
  let held = 0;
  // A lightly crushed, band-limited texture rather than an unfiltered noise blast.
  for (let index = 0; index < sampleCount; index++) {
    if (index % 4 === 0) {
      seed = (Math.imul(seed, 1664525) + 1013904223) | 0;
      held = ((seed >>> 0) / 0xffffffff * 2 - 1) * .7;
    }
    samples[index] = held;
  }
  const source = context.createBufferSource();
  source.buffer = buffer;
  const filter = context.createBiquadFilter();
  filter.type = 'bandpass';
  filter.frequency.value = segment.formant;
  filter.Q.value = .8;
  const envelope = context.createGain();
  envelope.gain.setValueAtTime(.0001, at);
  envelope.gain.linearRampToValueAtTime(segment.level, at + .006);
  envelope.gain.setValueAtTime(segment.level * .6, at + segment.duration * .45);
  envelope.gain.exponentialRampToValueAtTime(.0001, at + segment.duration);
  source.connect(filter);
  filter.connect(envelope);
  envelope.connect(destination);
  source.start(at);
  source.stop(at + segment.duration + .005);
  return source;
}


export function buildSpeechPlan(text, intent = 'welcome', random = Math.random) {
  const profile = profiles[intent] || profiles.welcome;
  const words = String(text).trim().split(/\s+/).filter(Boolean);
  const segments = [];
  const captions = [];
  let time = 0.12;
  let mechanicalCount = 0;
  // Short servo breath gives the voice a small mechanical body.
  segments.push({ kind: 'servo', at: 0, duration: 0.12, pitch: 95, endPitch: 145, level: 0.08 });
  if (words.length && random() < .32) {
    time += appendMechanicalAccent(segments, time, random() < .6 ? 'static' : 'servo', random) + .045;
    mechanicalCount++;
  }
  for (let wordIndex = 0; wordIndex < words.length; wordIndex++) {
    const word = words[wordIndex];
    captions.push({ text: word, at: time });
    const syllables = splitSyllables(word);
    const wordPitch = profile.pitch * (0.88 + random() * 0.25);
    for (let index = 0; index < syllables.length; index++) {
      const last = wordIndex === words.length - 1 && index === syllables.length - 1;
      const question = /[?？]$/.test(word);
      const duration = (0.095 + Math.min(syllables[index].length, 5) * 0.016 + random() * 0.055) / profile.pace;
      const pitch = wordPitch * (index % 2 ? 1.08 : 0.94);
      const endPitch = last ? pitch * (question ? 1.48 : profile.lift * 0.72) :
        pitch * (index % 2 ? 0.88 : 1.16);
      segments.push({
        kind: index % 3 === 1 ? 'rounded' : 'voice', at: time, duration,
        pitch, endPitch, level: 0.19 + random() * 0.035,
        formant: 650 + random() * 700, word: wordIndex,
      });
      time += duration + (0.025 + random() * 0.035) / profile.pace;
    }
    if (wordIndex < words.length - 1) {
      time += /[,;:!?…]$/.test(word) ? 0.20 + random() * 0.10 : 0.055 + random() * 0.075;
      if (mechanicalCount < 2 && /[,!?]$/.test(word) && random() < .22) {
        const kind = random() < .5 ? 'static' : 'handshake';
        time += appendMechanicalAccent(segments, time, kind, random) + .045;
        mechanicalCount++;
      }
      // A low coo between some phrases, without repeating it after every word.
      if ((/[,!?]$/.test(word) || wordIndex % 4 === 3) && random() < 0.48) {
        segments.push({ kind: 'rounded', at: time, duration: 0.15, pitch: wordPitch * 0.72,
          endPitch: wordPitch * 0.92, level: 0.10, formant: 630 });
        time += 0.20;
      }
    }
  }
  const duration = segments.reduce((end, segment) => Math.max(end, segment.at + segment.duration), 0);
  return { text, intent, segments, captions, duration: duration + 0.14 };
}

export function buildReactionPlan(intent = 'curious', random = Math.random) {
  if (['static', 'handshake', 'servo'].includes(intent)) return buildMechanicalPlan(intent, random);
  if (['curious', 'thoughtful'].includes(intent) && random() < .12) {
    return { ...buildMechanicalPlan('handshake', random), intent };
  }
  const profile = profiles[intent] || profiles.curious;
  const shapes = {
    curious: [[0,.22,.82,1.35],[.28,.30,1.05,1.65]],
    happy: [[0,.12,1,1.22],[.17,.15,1.25,1.55],[.39,.23,1.48,1.18]],
    thoughtful: [[0,.40,.85,.76],[.55,.26,.95,1.14]],
    sleepy: [[0,.65,.85,.55]],
    welcome: [[0,.19,.80,1.25],[.25,.28,1.08,1.4]],
  };
  const shape = shapes[intent] || shapes.curious;
  const segments = shape.map(([at,duration,from,to], index) => ({
    kind: intent === 'sleepy' || index % 2 === 0 ? 'rounded' : 'voice',
    at, duration, pitch: profile.pitch * from * (.96 + random() * .08),
    endPitch: profile.pitch * to, level: .18, formant: 680 + index * 160,
  }));
  if (random() < .25) {
    const kind = intent === 'sleepy' ? 'servo' : (random() < .5 ? 'static' : 'handshake');
    const intro = buildMechanicalPlan(kind, random, true);
    for (const segment of segments) segment.at += intro.duration + .035;
    segments.unshift(...intro.segments);
  }
  return { text: '', intent, captions: [], segments,
    duration: Math.max(...segments.map(segment => segment.at + segment.duration)) + .12 };
}

export function schedulePlan(context, destination, plan, start, volume = 1) {
  const group = context.createGain();
  group.gain.value = clamp(volume, 0, 1);
  group.connect(destination);
  const nodes = [];
  let remaining = 0;
  let finishedScheduling = false;
  function track(node) {
    nodes.push(node);
    remaining++;
    node.addEventListener('ended', () => {
      remaining--;
      if (finishedScheduling && remaining === 0) group.disconnect();
    }, { once: true });
  }
  for (const segment of plan.segments) {
    const at = start + segment.at;
    const duration = segment.duration;
    if (segment.kind === 'static') {
      track(noiseSegment(context, group, segment, at));
      continue;
    }
    const oscillator = context.createOscillator();
    const envelope = context.createGain();
    const filter = context.createBiquadFilter();
    const vibrato = context.createOscillator();
    const depth = context.createGain();

    oscillator.type = segment.kind === 'data' ? 'square' :
      (segment.kind === 'voice' || segment.kind === 'motor' ? 'sawtooth' : 'sine');
    oscillator.frequency.setValueAtTime(segment.pitch, at);
    oscillator.frequency.exponentialRampToValueAtTime(Math.max(40, segment.endPitch), at + duration * .86);
    vibrato.frequency.value = segment.kind === 'servo' || segment.kind === 'motor' ? 26 : 5.4;
    depth.gain.value = segment.kind === 'data' ? 0 :
      (segment.kind === 'servo' || segment.kind === 'motor' ? 14 : segment.pitch * .019);
    vibrato.connect(depth);
    depth.connect(oscillator.frequency);

    filter.type = ['voice', 'motor', 'data'].includes(segment.kind) ? 'lowpass' : 'peaking';
    filter.frequency.setValueAtTime(segment.formant || 900, at);
    filter.frequency.linearRampToValueAtTime((segment.formant || 900) * .78, at + duration);
    filter.Q.value = segment.kind === 'voice' ? 1.65 : .65;
    filter.gain.value = 5;

    envelope.gain.setValueAtTime(.0001, at);
    envelope.gain.exponentialRampToValueAtTime(segment.level, at + Math.min(.035, duration * .22));
    envelope.gain.setValueAtTime(segment.level * .72, at + duration * .55);
    envelope.gain.exponentialRampToValueAtTime(.0001, at + duration);
    oscillator.connect(filter);
    filter.connect(envelope);
    envelope.connect(group);
    track(oscillator);
    track(vibrato);
    oscillator.start(at);
    vibrato.start(at);
    oscillator.stop(at + duration + .01);
    vibrato.stop(at + duration + .01);
  }
  // Track each short source as it is created, including noise BufferSources.
  finishedScheduling = true;
  if (remaining === 0) group.disconnect();
  let stopped = false;
  return {
    stop() {
      if (stopped) return;
      stopped = true;
      const now = context.currentTime;
      group.gain.cancelScheduledValues(now);
      group.gain.setTargetAtTime(0, now, .012);
      for (const node of nodes) {
        try { node.stop(now + .04); } catch { /* Node may have finished. */ }
      }
    },
  };
}

export const VOICE_LEVEL = 1.8;
export function createVoiceOutput(context, destination) {
  const input = context.createGain();
  input.gain.value = VOICE_LEVEL;
  const compressor = context.createDynamicsCompressor();
  compressor.threshold.value = -8;
  compressor.knee.value = 8;
  compressor.ratio.value = 8;
  compressor.attack.value = .003;
  compressor.release.value = .12;
  const makeup = context.createGain();
  makeup.gain.value = 1.4;
  input.connect(compressor);
  compressor.connect(makeup);
  const ceiling = context.createWaveShaper();
  const curve = new Float32Array(2049);
  for (let index = 0; index < curve.length; index++) {
    const value = index * 2 / (curve.length - 1) - 1;
    const magnitude = Math.abs(value);
    curve[index] = Math.sign(value) * (magnitude <= .8 ? magnitude :
      .8 + .14 * Math.tanh((magnitude - .8) / .14));
  }
  ceiling.curve = curve;
  makeup.connect(ceiling);
  ceiling.connect(destination);
  return input;
}

export class RobotVoice {
  constructor() {
    this.context = null;
    this.output = null;
    this.enabled = true;
    this.session = null;
    this.scheduled = null;
    this.sleepTimer = null;
    this.sleepTransition = null;
  }

  async unlock() {
    if (!this.enabled) return;
    if (!this.context) {
      const Audio = window.AudioContext || window.webkitAudioContext;
      if (!Audio) return;
      this.context = new Audio();
      this.output = createVoiceOutput(this.context, this.context.destination);
    }
    clearTimeout(this.sleepTimer);
    if (this.sleepTransition) await this.sleepTransition;
    if (this.context.state === 'suspended') await this.context.resume();
    if (this.context.state === 'running' && this.session && !this.scheduled &&
        this.session.elapsed() < this.session.plan.duration) {
      const start = this.context.currentTime + .045;
      this.scheduled = schedulePlan(this.context, this.output, this.session.plan, start, this.session.volume);
      this.session.elapsed = () => this.context.currentTime - start;
      this.session.audible = true;
    }
    this.sleepWhenFinished();
  }

  setEnabled(enabled) {
    this.enabled = Boolean(enabled);
    if (this.output) this.output.gain.setTargetAtTime(this.enabled ? VOICE_LEVEL : 0, this.context.currentTime, .025);
    if (this.enabled) this.unlock().catch(() => {});
  }

  play(plan, volume = 1) {
    clearTimeout(this.sleepTimer);
    this.scheduled?.stop();
    const audible = this.enabled && this.context?.state === 'running' && !this.sleepTransition;
    const audioStart = audible ? this.context.currentTime + .045 : 0;
    const wallStart = performance.now() / 1000 + .045;
    this.scheduled = audible ? schedulePlan(this.context, this.output, plan, audioStart, volume) : null;
    this.session = {
      plan, volume, audible,
      elapsed: audible ? () => this.context.currentTime - audioStart :
        () => performance.now() / 1000 - wallStart,
    };
    if (this.enabled && !audible) this.unlock().catch(() => {});
    this.sleepWhenFinished();
    return this.session;
  }

  sleepWhenFinished() {
    clearTimeout(this.sleepTimer);
    if (!this.context || this.context.state !== 'running') return;
    const session = this.session;
    const remaining = session ? Math.max(0, session.plan.duration - session.elapsed()) : 0;
    this.sleepTimer = setTimeout(() => {
      if (this.session !== session || this.context.state !== 'running') return;
      if (session) {
        // Let subtitle tails advance while the hardware audio clock is asleep.
        const elapsed = session.elapsed();
        const wall = performance.now() / 1000;
        session.elapsed = () => elapsed + performance.now() / 1000 - wall;
      }
      const transition = this.context.suspend().catch(() => {});
      this.sleepTransition = transition;
      transition.finally(() => {
        if (this.sleepTransition === transition) this.sleepTransition = null;
      });
    }, (remaining + .6) * 1000);
  }

  speak(text, options = {}) {
    const intent = options.intent || (/[?？]/.test(text) ? 'curious' : 'welcome');
    return this.play(buildSpeechPlan(text, intent), options.volume ?? 1);
  }

  react(intent) {
    return this.play(buildReactionPlan(intent), .68);
  }

  stop() {
    this.scheduled?.stop();
    this.session = null;
    this.scheduled = null;
    this.sleepWhenFinished();
  }
}
