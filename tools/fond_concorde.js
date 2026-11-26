/* Concorde stellaire : le fond algorithmique des videos de presentation (la philosophie : tools/fond_concorde.md).
 *
 *   const c = new Concorde(p, options)   p : une instance p5 (mode instance) dont le canevas est deja cree
 *   c.avancer(t)    fait avancer la simulation jusqu'a l'instant t (secondes) par pas fixes de 1/60 s :
 *                   la meme graine donne toujours la meme image au meme instant
 *   c.dessiner()    dessine l'etat courant sur le canevas
 *   c.regler(opts)  change des options et recommence au debut
 *
 * Options d'images (facultatives) : `portail` {image, images, pas, opacite}, une planche d'images empilees de haut en
 * bas (le fond anime des cartes de selection, en gros pixels) dessinee sous tout le reste ; `signe`, une image claire
 * sur fond transparent (le signe de la voie du personnage) qui remplace les etoiles des noeuds du halo.
 *
 * Calendrier (cale sur tools/presentation_perso.py) : le choeur monte du bas de l'ecran de `montee` a `impact`, une onde
 * de choc part du centre a `impact` et remet les phases a zero, puis le couplage de Kuramoto accorde les pulsations et
 * le choeur se range sur les trois orbites du halo.
 */
(function (racine) {
  'use strict';
  const TAU = Math.PI * 2, PAS = 1 / 60, CHOC = 2600;

  const DEFAUTS = {
    graine: 1,
    couleur: '#4a4fc4',      // la couleur du personnage
    accent: '#ffe2a0',       // les eclats du halo
    fond: '#0b0d18',
    particules: 2600,
    choeur: 0.62,            // part des particules qui monte puis rejoint le halo (les autres sont des braises)
    eclats: 12,              // les noeuds de l'orbite exterieure
    couplage: 5,             // force d'accord des phases apres l'impact
    traine: 0.85,            // 0 : des points, 1 : de longues trainees
    turbulence: 1,
    montee: 0.3, impact: 1.2,
    centre: [0.5, 0.43],     // centre du halo, en fraction de la largeur et de la hauteur
    demiAxes: [0.27, 0.34],
    choc: [0.5, 0.56],       // d'ou part l'onde de choc
    lueur: 1,                // force de la lueur du fond
    portail: null,
    signe: null, tailleSigne: 76,
  };
  // Les trois orbites, en rapports d'intervalles purs : quinte, quarte, unisson.
  const ORBITES = [2 / 3, 3 / 4, 1], POIDS = [0.22, 0.3, 0.48], SENS = [1, -1, 1];

  const rgb = h => [0, 2, 4].map(i => parseInt(h.replace('#', '').slice(i, i + 2), 16));
  const melange = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
  const css = (c, a) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${Math.max(0, Math.min(1, a)).toFixed(3)})`;
  function lisse(e0, e1, x) { const t = Math.min(1, Math.max(0, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); }

  class Concorde {
    constructor(p, options) {
      this.p = p;
      this.W = p.width; this.H = p.height;
      this.traces = p.createGraphics(this.W, this.H);
      this.traces.pixelDensity(1);
      this.regler(options);
    }

    regler(options) {
      this.o = Object.assign({}, DEFAUTS, this.o || {}, options || {});
      this.reinit();
    }

    reinit() {
      const p = this.p, o = this.o, W = this.W, H = this.H, n = Math.round(o.particules);
      p.randomSeed(o.graine); p.noiseSeed(o.graine);
      this.t = 0; this.n = n;
      const F = () => new Float32Array(n);
      this.x = F(); this.y = F(); this.px = F(); this.py = F(); this.vx = F(); this.vy = F();
      this.th = F(); this.om = F(); this.z = F(); this.rho = F();
      this.voie = new Uint8Array(n); this.ch = new Uint8Array(n);
      this.cx = o.centre[0] * W; this.cy = o.centre[1] * H; this.rx = o.demiAxes[0] * W; this.ry = o.demiAxes[1] * H;
      this.c0 = rgb(o.couleur); this.cf = rgb(o.fond); this.ca = rgb(o.accent);
      this.c1 = melange(this.c0, [255, 255, 255], 0.35);
      this.kx = o.choc[0] * W; this.ky = o.choc[1] * H;
      this.signe = o.signe ? this.preparerSigne(o.signe) : null;
      for (let i = 0; i < n; i++) {
        const choeur = p.random() < o.choeur;
        this.ch[i] = choeur ? 1 : 0;
        if (choeur) {
          this.z[i] = p.random(0.55, 1);
          this.x[i] = W / 2 + p.randomGaussian(0, W * 0.15);
          this.y[i] = H * (0.75 + p.random(0.9));
          this.vy[i] = -p.random(40, 120);
        } else {
          this.z[i] = 0.2 + 0.75 * Math.pow(p.random(), 1.6);
          this.x[i] = p.random(-40, W + 40);
          this.y[i] = p.random(-40, H + 40);
          this.vy[i] = -p.random(8, 40) * this.z[i];
        }
        this.px[i] = this.x[i]; this.py[i] = this.y[i];
        this.th[i] = p.random(TAU);
        this.om[i] = TAU * (0.55 + p.randomGaussian(0, 0.09));
        let r = p.random(), v = 0;
        while (v < 2 && r > POIDS[v]) { r -= POIDS[v]; v++; }
        this.voie[i] = v;
        this.rho[i] = p.randomGaussian(0, 0.035);
      }
      this.R = 0; this.psi = 0; this.pouls = 0;
      this.traces.clear();
      this.traces.background(0);
    }

    // Le signe en blanc, et sa lueur floutee a la couleur des eclats : prepares une fois.
    preparerSigne(img) {
      const src = img.canvas || img.elt || img, n = 192, m = 64;
      const blanc = document.createElement('canvas');
      blanc.width = blanc.height = n;
      blanc.getContext('2d').drawImage(src, 0, 0, n, n);
      const lueur = document.createElement('canvas');
      lueur.width = lueur.height = n + 2 * m;
      const g = lueur.getContext('2d');
      g.filter = 'blur(16px)';
      g.drawImage(blanc, m, m);
      g.filter = 'none';
      g.globalCompositeOperation = 'source-in';
      g.fillStyle = css(this.ca, 1);
      g.fillRect(0, 0, n + 2 * m, n + 2 * m);
      return { blanc, lueur, k: (n + 2 * m) / n };
    }

    avancer(t) { while (this.t < t - PAS / 2) this.pas(); }

    // Les instants de la sequence, en fonction du temps.
    monte(t) { const o = this.o; return lisse(o.montee - 0.15, o.montee + 0.15, t) * (1 - lisse(o.impact - 0.12, o.impact + 0.08, t)); }
    angleEclats(t) { return -Math.PI / 2 + 0.06 * t; }

    pas() {
      const p = this.p, o = this.o, W = this.W, H = this.H, n = this.n, dt = PAS;
      const t = (this.t += dt);
      const monte = this.monte(t), attire = lisse(o.impact, o.impact + 1.1, t);
      const front0 = Math.max(0, t - dt - o.impact) * CHOC, front1 = Math.max(0, t - o.impact) * CHOC;
      const K = t < o.impact ? 0 : o.couplage * (1 + 3 * Math.exp(-(t - o.impact) * 2));
      const cx = this.cx, cy = this.cy, rx = this.rx, ry = this.ry;
      const secteur = TAU / o.eclats, phi0 = this.angleEclats(t);
      const { x, y, px, py, vx, vy, th, om, z, ch, voie, rho } = this;

      // Le champ moyen des phases (Kuramoto) : R mesure l'accord, psi la phase commune.
      let sc = 0, ss = 0;
      for (let i = 0; i < n; i++) { sc += Math.cos(th[i]); ss += Math.sin(th[i]); }
      this.R = Math.hypot(sc, ss) / n; this.psi = Math.atan2(ss, sc);
      const R = this.R, psi = this.psi;

      for (let i = 0; i < n; i++) {
        let X = x[i], Y = y[i], VX = vx[i], VY = vy[i];
        const Z = z[i], C = ch[i];
        px[i] = X; py[i] = Y;
        // Le courant libre : la colonne ascendante et les remous d'un champ d'angles qui derive.
        const a = p.noise(X * 0.0018, Y * 0.0018, t * 0.12 + (C ? 0 : 7.3)) * TAU * 2;
        const remous = o.turbulence * (C ? 70 : 34) * Z;
        const colonne = Math.exp(-Math.pow((X - W / 2) / (W * 0.2), 2));
        const haut = C ? 1700 * monte * (0.55 + 0.45 * colonne) * (0.6 + 0.4 * Z) + 30 : (22 + 420 * monte * colonne) * Z;
        // Plus le courant est fort, plus il ondule : les trajectoires de la montee serpentent au lieu de tomber droit.
        let ax = (Math.cos(a) * (remous + 0.18 * haut) - VX) * 3.5, ay = (Math.sin(a) * remous - haut - VY) * 3.5;
        // Le choeur se range sur son orbite : ressort amorti vers l'ellipse, alignement sur la vitesse orbitale.
        if (C && attire > 0) {
          const ex = (X - cx) / rx, ey = (Y - cy) / ry, phi = Math.atan2(ey, ex);
          const L = ORBITES[voie[i]] * (1 + rho[i]);
          let d = (((phi - phi0) % secteur) + secteur) % secteur;
          d = Math.min(d, secteur - d) / (secteur / 2);
          const Om = SENS[voie[i]] * 0.55 / Math.pow(ORBITES[voie[i]], 1.5) * (0.22 + 0.78 * lisse(0, 1, d));
          const cs = Math.cos(phi), sn = Math.sin(phi);
          const ox = 9 * (cx + rx * L * cs - X) + 5 * (-Om * rx * L * sn - VX);
          const oy = 9 * (cy + ry * L * sn - Y) + 5 * (Om * ry * L * cs - VY);
          ax = ax * (1 - attire) + (ox + Math.cos(a) * remous * 0.6) * attire;
          ay = ay * (1 - attire) + (oy + Math.sin(a) * remous * 0.6) * attire;
        }
        VX += ax * dt; VY += ay * dt;
        // L'onde de choc : elle projette la particule et remet sa phase a zero quand son front la traverse.
        const dx = X - this.kx, dy = Y - this.ky, dist = Math.hypot(dx, dy) || 1;
        if (front1 > 0 && dist > front0 && dist <= front1) {
          const k = (0.4 + 0.6 * Math.exp(-dist / 900)) * 900 * Z;
          VX += dx / dist * k; VY += dy / dist * k;
          th[i] = 0;
        }
        const v = Math.hypot(VX, VY);
        if (v > 2600) { VX *= 2600 / v; VY *= 2600 / v; }
        X += VX * dt; Y += VY * dt;
        // Les braises qui sortent par le haut renaissent en bas, sans trainee.
        if (!C && Y < -60) { Y = H + 40; X = p.random(-40, W + 40); px[i] = X; py[i] = Y; }
        if (!C && X < -80) { X += W + 140; px[i] = X; }
        if (!C && X > W + 80) { X -= W + 140; px[i] = X; }
        x[i] = X; y[i] = Y; vx[i] = VX; vy[i] = VY;
        th[i] += (om[i] + K * R * Math.sin(psi - th[i])) * dt;
      }
      this.tracer(t);
    }

    // Les trainees : la couche s'efface d'une fraction a chaque pas, puis chaque particule y ajoute son segment.
    tracer(t) {
      const o = this.o, n = this.n, g = this.traces.drawingContext;
      const { x, y, px, py, vx, vy, th, z, ch } = this;
      g.globalCompositeOperation = 'source-over';
      // Les trainees s'allongent quand le choeur est en orbite : le halo garde la memoire de sa rotation.
      const efface = (0.35 + (0.06 - 0.35) * o.traine) * (1 - 0.4 * lisse(o.impact, o.impact + 1.1, t));
      g.fillStyle = `rgba(0,0,0,${efface.toFixed(3)})`;
      g.fillRect(0, 0, this.W, this.H);
      g.globalCompositeOperation = 'lighter';
      g.lineCap = 'round';
      const allume = lisse(0, 0.7, t), blanc = [255, 255, 255];
      let somme = 0, nb = 0;
      for (let i = 0; i < n; i++) {
        const b = 0.32 + 0.68 * Math.pow((1 + Math.cos(th[i])) / 2, 2);
        if (ch[i]) { somme += b; nb++; }
        const al = allume * b * (0.25 + 0.75 * z[i]) * (ch[i] ? 1 : 0.55);
        if (al < 0.01) continue;
        const chaud = Math.min(1, b * 0.7 + Math.hypot(vx[i], vy[i]) / 3000);
        g.strokeStyle = css(melange(this.c1, blanc, chaud), al);
        g.lineWidth = 0.6 + 1.9 * z[i] * (ch[i] ? 1 : 0.7);
        g.beginPath(); g.moveTo(px[i], py[i]); g.lineTo(x[i] + 0.01, y[i]); g.stroke();
      }
      this.pouls = nb ? somme / nb : 0;
    }

    dessiner() {
      const o = this.o, ctx = this.p.drawingContext, W = this.W, H = this.H, t = this.t;
      const allume = lisse(0, 0.9, t), attire = lisse(o.impact, o.impact + 1.1, t);
      const eclair = t > o.impact ? Math.exp(-(t - o.impact) * 3.2) : 0;
      ctx.save();
      ctx.globalCompositeOperation = 'source-over';
      ctx.fillStyle = css(this.cf, 1);
      ctx.fillRect(0, 0, W, H);
      // Le portail des cartes, en gros pixels nets.
      if (o.portail) {
        const P = o.portail, img = P.image.canvas || P.image, fh = img.height / P.images;
        const k = Math.floor(t / P.pas) % P.images;
        ctx.imageSmoothingEnabled = false;
        ctx.globalAlpha = allume * (P.opacite ?? 1);
        ctx.drawImage(img, 0, k * fh, img.width, fh, 0, 0, W, H);
        ctx.globalAlpha = 1;
        ctx.imageSmoothingEnabled = true;
      }
      // La lueur du fond, qui respire avec la pulsation commune du choeur.
      const I = o.lueur * (allume * (0.7 + 0.45 * this.pouls * attire) + 0.7 * eclair);
      const lueur = ctx.createRadialGradient(this.kx, this.ky, 0, this.kx, this.ky, W * 0.6);
      lueur.addColorStop(0, css(this.c0, 0.62 * I));
      lueur.addColorStop(0.42, css(this.c0, 0.2 * I));
      lueur.addColorStop(1, css(this.c0, 0));
      ctx.fillStyle = lueur;
      ctx.fillRect(0, 0, W, H);
      ctx.globalCompositeOperation = 'lighter';
      // La colonne de lumiere de la montee.
      const m = this.monte(t);
      if (m > 0.01) {
        // Une ellipse tres haute posee sur le bord du bas : la lumiere s'eteint en douceur sur les cotes et vers le haut.
        ctx.save();
        ctx.translate(W / 2, H);
        ctx.scale(1, H * 1.05 / 260);
        const col = ctx.createRadialGradient(0, 0, 0, 0, 0, 260);
        col.addColorStop(0, css([255, 255, 255], 0.5 * m));
        col.addColorStop(0.35, css(this.c1, 0.32 * m));
        col.addColorStop(1, css(this.c0, 0));
        ctx.fillStyle = col;
        ctx.fillRect(-260, -260, 520, 260);
        ctx.restore();
      }
      ctx.drawImage(this.traces.elt, 0, 0);
      // L'onde de choc.
      const age = t - o.impact;
      if (age > 0 && age < 0.6) {
        const r = age * CHOC, a = 1 - age / 0.6;
        for (const [w, al] of [[34, 0.08], [12, 0.22], [3, 0.8]]) {
          ctx.strokeStyle = css(melange(this.c1, [255, 255, 255], 0.6), al * a);
          ctx.lineWidth = w;
          ctx.beginPath(); ctx.arc(this.kx, this.ky, r, 0, TAU); ctx.stroke();
        }
      }
      // Les eclats : ils s'allument quand l'onde les atteint, puis battent avec le choeur.
      const phi0 = this.angleEclats(t);
      for (let k = 0; k < o.eclats; k++) {
        const phi = phi0 + k * TAU / o.eclats;
        const ex = this.cx + this.rx * Math.cos(phi), ey = this.cy + this.ry * Math.sin(phi);
        const t0 = o.impact + Math.hypot(ex - this.kx, ey - this.ky) / CHOC;
        const e = lisse(t0, t0 + 0.3, t) * (0.55 + 0.6 * this.pouls) + 1.2 * Math.exp(-Math.max(0, t - t0) * 6) * (t > t0 ? 1 : 0);
        if (e < 0.01) continue;
        if (this.signe) {
          // Le signe de la voie, qui tourne lentement sur lui-meme dans sa lueur.
          const S = this.signe, taille = o.tailleSigne * (0.8 + 0.25 * Math.min(e, 1.6));
          ctx.save();
          ctx.translate(ex, ey);
          ctx.globalAlpha = Math.min(1, 0.85 * e);
          ctx.drawImage(S.lueur, -taille * S.k / 2, -taille * S.k / 2, taille * S.k, taille * S.k);
          ctx.rotate(0.35 * t + k * 0.7);
          ctx.globalAlpha = Math.min(1, e);
          ctx.drawImage(S.blanc, -taille / 2, -taille / 2, taille, taille);
          ctx.restore();
          continue;
        }
        const halo = ctx.createRadialGradient(ex, ey, 0, ex, ey, 34 * e);
        halo.addColorStop(0, css(this.ca, 0.75));
        halo.addColorStop(0.3, css(this.ca, 0.25));
        halo.addColorStop(1, css(this.ca, 0));
        ctx.fillStyle = halo;
        ctx.fillRect(ex - 34 * e, ey - 34 * e, 68 * e, 68 * e);
        ctx.strokeStyle = css(melange(this.ca, [255, 255, 255], 0.5), Math.min(1, 0.9 * e));
        ctx.lineWidth = 1.6;
        ctx.beginPath();
        ctx.moveTo(ex - 26 * e, ey); ctx.lineTo(ex + 26 * e, ey);
        ctx.moveTo(ex, ey - 16 * e); ctx.lineTo(ex, ey + 16 * e);
        ctx.stroke();
        ctx.fillStyle = css([255, 255, 255], Math.min(1, e));
        ctx.beginPath(); ctx.arc(ex, ey, 2.6, 0, TAU); ctx.fill();
      }
      ctx.restore();
    }
  }

  Concorde.DEFAUTS = DEFAUTS;
  racine.Concorde = Concorde;
})(typeof window !== 'undefined' ? window : globalThis);
