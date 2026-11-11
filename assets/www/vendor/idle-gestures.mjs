const clamp = (value, low, high) => Math.max(low, Math.min(high, value));
const smooth = value => {
  const t = clamp(value, 0, 1);
  return t * t * (3 - 2 * t);
};
const windowPulse = (time, start, end, edge = .12) =>
  smooth((time - start) / edge) * smooth((end - time) / edge);

export const IDLE_GESTURES = {
  wink: { name: 'Muzip göz kırpma', duration: 2.6, sound: 'welcome' },
  curious: { name: 'Meraklı bakış', duration: 3.8, sound: 'curious' },
  giggle: { name: 'Neşeli kıkırdama', duration: 3.2, sound: 'happy' },
  sleepy: { name: 'Uykulu esneme', duration: 4.8, sound: 'sleepy' },
};

export function idlePose(kind, progress) {
  const t = clamp(progress, 0, 1);
  const envelope = Math.sin(Math.PI * t);
  const eye = () => ({ open: 1, wide: 1, tilt: 0, x: 0, y: 0, smile: 0 });
  const pose = { kind, progress: t, strength: envelope, eyes: [eye(), eye()], gazeX: 0, gazeY: 0 };
  const [left, right] = pose.eyes;
  if (kind === 'wink') {
    const wink = Math.max(windowPulse(t, .27, .52, .065), windowPulse(t, .64, .82, .05) * .65);
    left.open = 1 - wink * .93;
    left.tilt = -.12 * envelope;
    right.wide = 1 + envelope * .08;
    right.tilt = .09 * envelope;
    pose.gazeX = -.22 * envelope;
    right.y = -.05 * envelope;
  } else if (kind === 'curious') {
    const scan = Math.sin(t * Math.PI * 2.4) * envelope;
    left.open = 1 + .17 * envelope;
    left.wide = 1 + .08 * envelope;
    right.open = 1 - .32 * envelope;
    left.tilt = -.16 * envelope;
    right.tilt = -.10 * envelope;
    pose.gazeX = scan * .55;
    pose.gazeY = -.28 * envelope;
    left.y = -.12 * envelope;
    right.y = .06 * envelope;
  } else if (kind === 'giggle') {
    const smile = windowPulse(t, .13, .87, .18);
    const bounce = Math.sin(t * Math.PI * 10) * .075 * envelope;
    left.smile = right.smile = smile;
    left.y = right.y = bounce - .1 * envelope;
    left.tilt = -.08 * envelope;
    right.tilt = .08 * envelope;
    pose.gazeY = -.06 * envelope;
  } else if (kind === 'sleepy') {
    const droop = windowPulse(t, .12, .76, .19);
    const wake = windowPulse(t, .80, .95, .055);
    left.open = right.open = 1 - .80 * droop + .12 * wake;
    left.y = right.y = .19 * droop;
    left.tilt = .06 * droop;
    right.tilt = -.06 * droop;
    pose.gazeY = .36 * droop;
  }
  return pose;
}

export class IdleAnimator {
  constructor(random = Math.random) {
    this.random = random;
    this.active = null;
    this.nextAt = null;
    this.remaining = [];
    this.last = null;
  }

  preview(kind, now) {
    if (!IDLE_GESTURES[kind]) return false;
    this.active = { kind, at: now };
    this.nextAt = null;
    return true;
  }

  update(now, { hasFace = false, hasLock = false, review = false, speaking = false } = {}) {
    if (hasFace || hasLock || review || speaking) {
      const interrupted = Boolean(this.active);
      this.active = null;
      this.nextAt = now + 10 + this.random() * 8;
      return { pose: null, interrupted };
    }
    if (this.active) {
      const definition = IDLE_GESTURES[this.active.kind];
      const progress = (now - this.active.at) / definition.duration;
      if (progress < 1) return { pose: idlePose(this.active.kind, progress) };
      this.active = null;
      this.nextAt = now + 14 + this.random() * 12;
    }
    if (this.nextAt === null) this.nextAt = now + 8 + this.random() * 6;
    if (now < this.nextAt) return { pose: null };
    if (!this.remaining.length) this.remaining = Object.keys(IDLE_GESTURES);
    const choices = this.remaining.filter(kind => kind !== this.last);
    const kind = choices[Math.floor(this.random() * choices.length)];
    this.remaining = this.remaining.filter(item => item !== kind);
    this.last = kind;
    this.active = { kind, at: now };
    return { pose: idlePose(kind, 0), started: kind };
  }
}

export function drawIdleAccents(ctx, width, height, pose) {
  if (!pose || pose.strength < .05) return;
  const strength = pose.strength;
  const size = Math.min(width, height);
  ctx.save();
  ctx.globalAlpha = strength * .8;
  ctx.strokeStyle = '#91ffb1';
  ctx.fillStyle = '#a4ffc1';
  ctx.lineWidth = Math.max(1.5, size * .003);
  ctx.shadowColor = '#38dc73';
  ctx.shadowBlur = size * .018;

  function sparkle(x, y, radius) {
    ctx.beginPath();
    ctx.moveTo(x - radius, y);
    ctx.quadraticCurveTo(x, y, x, y - radius);
    ctx.quadraticCurveTo(x, y, x + radius, y);
    ctx.quadraticCurveTo(x, y, x, y + radius);
    ctx.quadraticCurveTo(x, y, x - radius, y);
    ctx.stroke();
  }

  if (pose.kind === 'wink') {
    sparkle(width * .28, height * .30, size * .025 * strength);
  } else if (pose.kind === 'curious') {
    ctx.font = '600 ' + Math.round(size * .075) + 'px "Segoe UI", sans-serif';
    ctx.fillText('?', width * .70, height * .29 - strength * size * .025);
  } else if (pose.kind === 'giggle') {
    const drift = Math.sin(pose.progress * Math.PI * 4) * size * .012;
    sparkle(width * .27, height * .30 + drift, size * .023);
    sparkle(width * .73, height * .29 - drift, size * .017);
    for (const x of [width * .32, width * .68]) {
      ctx.beginPath();
      ctx.ellipse(x, height * .62, size * .025, size * .007, 0, 0, Math.PI * 2);
      ctx.fill();
    }
  } else if (pose.kind === 'sleepy') {
    const drift = pose.progress * size * .06;
    for (let index = 0; index < 3; index++) {
      ctx.globalAlpha = strength * (.8 - index * .18);
      ctx.font = '600 ' + Math.round(size * (.025 + index * .014)) + 'px "Segoe UI", sans-serif';
      ctx.fillText('z', width * .69 + index * size * .032, height * .38 - index * size * .045 - drift);
    }
  }
  ctx.restore();
}
