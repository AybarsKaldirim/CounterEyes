export function smoothContactTarget(previous, desired, dt) {
  if (!previous) return { ...desired };
  const distance = Math.hypot(desired.x - previous.x, desired.y - previous.y);
  if (distance < .018) return previous;
  const amount = 1 - Math.exp(-Math.max(0, Math.min(dt, .1)) / .075);
  return {
    x: previous.x + (desired.x - previous.x) * amount,
    y: previous.y + (desired.y - previous.y) * amount,
  };
}

export class ContactAffection {
  constructor() {
    this.target = null;
    this.startedAt = null;
    this.lastLookingAt = -Infinity;
    this.active = false;
    this.intensity = 0;
    this.confirmedAt = 0;
    this.lastSoundAt = -Infinity;
    this.pendingSound = false;
  }

  reset(target = null) {
    this.target = target;
    this.startedAt = null;
    this.lastLookingAt = -Infinity;
    this.active = false;
    this.intensity = 0;
    this.pendingSound = false;
  }

  update(now, dt, { target, looking, locked, review = false }) {
    if (target !== this.target) this.reset(target);
    if (!locked || review) {
      this.active = false;
      this.startedAt = null;
      this.pendingSound = false;
    } else if (looking) {
      if (this.startedAt === null || now - this.lastLookingAt > .28) {
        this.startedAt = now;
        this.active = false;
        this.pendingSound = false;
      }
      this.lastLookingAt = now;
      if (!this.active && now - this.startedAt >= .65) {
        this.active = true;
        this.confirmedAt = now;
        this.pendingSound = true;
      }
    } else if (now - this.lastLookingAt > .28) {
      this.active = false;
      this.startedAt = null;
      this.pendingSound = false;
    }
    const age = Math.max(0, now - this.confirmedAt);
    const showing = this.active && age < 2;
    const destination = showing ? Math.min(1, (2 - age) / .25) : 0;
    const amount = 1 - Math.exp(-Math.max(0, dt) / (this.active ? .20 : .18));
    this.intensity += (destination - this.intensity) * amount;
    if (this.active && age >= 2) this.intensity = 0;
    return {
      active: showing,
      intensity: this.intensity,
      age,
      soundDue: this.active && this.pendingSound && now - this.lastSoundAt >= 12,
    };
  }

  soundPlayed(now) {
    this.lastSoundAt = now;
    this.pendingSound = false;
  }
}

export function drawContactHearts(ctx, eyes, radius, contact, panel) {
  if (!contact || contact.intensity < .02 || panel > .02) return;
  const intensity = contact.intensity;

  function heart(x, y, size, alpha, tilt) {
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(tilt);
    ctx.globalAlpha = alpha;
    ctx.shadowColor = '#40ed7c';
    ctx.shadowBlur = size * .55;
    const fill = ctx.createLinearGradient(0, -size * .7, 0, size * .7);
    fill.addColorStop(0, '#b9ffcf');
    fill.addColorStop(.5, '#53f58b');
    fill.addColorStop(1, '#1fb862');
    ctx.fillStyle = fill;
    ctx.beginPath();
    ctx.moveTo(0, size * .60);
    ctx.bezierCurveTo(-size * 1.15, -.10 * size, -size * .52, -size * .98, 0, -size * .40);
    ctx.bezierCurveTo(size * .52, -size * .98, size * 1.15, -.10 * size, 0, size * .60);
    ctx.fill();
    ctx.restore();
  }

  for (let index = 0; index < 4; index++) {
    const side = index % 2 === 0 ? -1 : 1;
    const eye = eyes[index % 2];
    const phase = (contact.age * .60 + Math.floor(index / 2) * .51) % 1;
    const envelope = Math.sin(Math.PI * phase);
    const drift = Math.sin(phase * Math.PI * 1.5 + index) * radius * .10;
    const x = eye.x + side * radius * (.66 + Math.floor(index / 2) * .23) + drift;
    const y = eye.y - radius * (.63 + phase * .88);
    const size = radius * (.14 + Math.floor(index / 2) * .035);
    heart(x, y, size, intensity * envelope * .85, side * .16);
  }
  // A small, slow pulse stays close to each eye while the floating hearts rise.
  const pulse = .9 + Math.sin(contact.age * Math.PI * 2.2) * .10;
  for (let index = 0; index < eyes.length; index++) {
    const eye = eyes[index];
    const side = index === 0 ? -1 : 1;
    heart(eye.x + side * radius * .68, eye.y + radius * .44,
      radius * .095 * pulse, intensity * .68, side * .16);
  }
}
