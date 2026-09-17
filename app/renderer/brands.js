// Launcher identities for the picker and the install guides. The glyphs are
// simple original drawings that evoke each launcher; they are not the
// launchers' own logo files.

export const VIDEO_URL = 'https://www.youtube.com/watch?v=y4ufGPTw_G4';

const glyphs = {
  dawn: `<svg viewBox="0 0 48 48" fill="none"><defs><linearGradient id="dg" x1="8" y1="40" x2="40" y2="8" gradientUnits="userSpaceOnUse"><stop stop-color="#F97316"/><stop offset="1" stop-color="#FFD24A"/></linearGradient></defs><g transform="rotate(-10 24 26)"><path d="M10 16 26 11l12 6v18l-12 6-16-5V16Z" fill="#2a2118"/><path d="M10 16 26 21v20l-16-5V16Z" fill="url(#dg)"/><path d="M26 21 38 17v18l-12 6V21Z" fill="#E8650F"/><path d="M10 16 26 11l12 6-12 4-16-5Z" fill="#FFE08A"/><rect x="15.5" y="23" width="5" height="10.5" rx="1" transform="skewY(17) translate(0 -5.4)" fill="#20170f"/></g><g stroke="#FFD24A" stroke-width="2.4" stroke-linecap="round"><path d="M37 6.5v3.4"/><path d="M42.6 9.6l-2.3 2.3"/><path d="M44.5 15.6h-3.3"/></g></svg>`,
  lunar: `<svg viewBox="0 0 48 48" fill="none"><path d="M30.5 5.5A19.5 19.5 0 1 0 42.6 33 15.6 15.6 0 0 1 30.5 5.5Z" fill="#fff"/><path d="M30.5 5.5 22 17l4.6 11.4L42.6 33A15.6 15.6 0 0 1 30.5 5.5Z" fill="#cfd6e4"/><path d="M11.5 12.6 22 17l-8.2 12.6L5 24.4a19.5 19.5 0 0 1 6.5-11.8Z" fill="#e6eaf2"/><path d="m13.8 29.6 12.8-1.2L24 43.5a19.5 19.5 0 0 1-10.2-13.9Z" fill="#b9c2d4"/></svg>`,
  modrinth: `<svg viewBox="0 0 48 48" fill="none" stroke="#1BD96A" stroke-width="3.6" stroke-linecap="round" stroke-linejoin="round"><path d="M43 24a19 19 0 1 1-5.6-13.4"/><path d="M33.6 34.4A13.2 13.2 0 1 1 36.4 19"/><path d="m24 24 6.2-3.4 3.4-6.6"/><path d="M24 24l-6.4 3-1.2 7.2"/><path d="m24 24-2.2-6.8-6.6-2.4"/><path d="M38.6 27.6 43 24"/></svg>`,
  other: `<svg viewBox="0 0 48 48" fill="none"><rect x="7" y="9" width="34" height="30" rx="5" fill="#2b2f3a" stroke="#596073" stroke-width="2"/><path d="M7 17h34" stroke="#596073" stroke-width="2"/><path d="M14 25h9v9h-9zM26 25h8M26 30h8" stroke="#aeb6c8" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="13" r="1.3" fill="#ff6a5a"/><circle cx="16.4" cy="13" r="1.3" fill="#ffc04a"/><circle cx="20.8" cy="13" r="1.3" fill="#52d17c"/></svg>`,
};

export const BRANDS = {
  dawn: {
    id: 'dawn',
    name: 'Dawn',
    sub: 'One click: we can install it for you',
    glyph: glyphs.dawn,
    accent: '#FFB02B',
    accent2: '#F97316',
    install: 'auto',
    intro: 'Dawn keeps mods per profile. Make a Fabric 1.21.11 profile once, then add the PVPTraining mod file to it.',
    steps: [
      { title: 'Open Profiles', body: 'In Dawn, go to the <b>Profiles</b> tab and press the new-profile button. Choose <b>Java</b>, then <b>Custom profile</b>.' },
      { title: 'Fabric, version 1.21.11', body: 'Name it <b>PVPTraining</b>, keep the loader on <b>Fabric</b>, pick game version <b>1.21.11</b> and press <b>Create profile</b>.', version: true },
      { title: 'Add the mod file', body: 'Pick your profile below and press <b>Install</b>. By hand: press the gear on the profile, then <b>Edit</b>, open the profile folder and drop the PVPTraining jar into <b>.minecraft/mods</b>.', jar: true, targets: true },
      { title: 'Launch the profile', body: 'Start the profile and wait for the title screen. Then come back here and press <b>Connect</b>.' },
    ],
    note: 'Dawn is new, so button names may shift between updates. If you cannot find the profile folder, save the mod file with the button below and use Add content inside the profile editor.',
  },
  lunar: {
    id: 'lunar',
    name: 'Lunar Client',
    sub: 'Needs the Fabric add-on',
    glyph: glyphs.lunar,
    accent: '#00C3FF',
    accent2: '#3BBE54',
    install: 'drag',
    intro: 'Lunar loads your own mods only when the Fabric add-on is selected. After that it is one drag and drop.',
    steps: [
      { title: 'Pick 1.21.11 with Fabric', body: 'Open the <b>version selector</b> (the puzzle-piece button beside Launch), choose <b>1.21.11</b> and select the <b>Fabric</b> add-on.', version: true },
      { title: 'Open the Mods panel', body: 'Press the <b>gear</b> at the bottom right of the version selector, then choose <b>Mods</b> at the top.' },
      { title: 'Drag the mod in', body: 'Drag the PVPTraining jar from this window and drop it onto Lunar\'s Mods panel. Make sure its <b>Enabled</b> box stays ticked.', jar: true },
      { title: 'Launch', body: 'Press <b>Launch</b>, wait for the title screen, then come back here and press <b>Connect</b>.' },
    ],
    note: 'Lunar already ships Fabric API with its add-on. PVPTraining carries its own copy too, and Fabric picks the newer one automatically.',
  },
  modrinth: {
    id: 'modrinth',
    name: 'Modrinth App',
    sub: 'One click: we can install it for you',
    glyph: glyphs.modrinth,
    accent: '#1BD96A',
    accent2: '#00AF5C',
    install: 'auto',
    intro: 'Create a Fabric 1.21.11 instance and PVPTraining can drop the mod into it for you.',
    steps: [
      { title: 'Create the instance', body: 'In the Modrinth App press <b>+</b> to create an instance. Set the loader to <b>Fabric</b> and the game version to <b>1.21.11</b>. Name it <b>PVPTraining</b>.', version: true },
      { title: 'Install the mod', body: 'Pick your instance below and press <b>Install</b>. Or do it by hand: open the instance, go to <b>Content</b>, press the arrow beside <b>Install content</b> and choose <b>Add from file</b>. Dropping the jar on the Content page works too.', jar: true, targets: true },
      { title: 'Play', body: 'Press <b>Play</b> on the instance and wait for the title screen. Then come back here and press <b>Connect</b>.' },
    ],
    note: 'The instance has to be a Fabric one. A vanilla instance will not show mod options at all.',
  },
  other: {
    id: 'other',
    name: 'Other',
    sub: 'Minecraft Launcher, Prism, anything else',
    glyph: glyphs.other,
    accent: '#c5cbd9',
    accent2: '#8d95a8',
    install: 'auto',
    intro: 'Any launcher that can run Fabric 1.21.11 works. This short video walks through installing Fabric and adding a mod.',
    video: true,
    steps: [
      { title: 'Install Fabric for 1.21.11', body: 'Follow the video: download the Fabric Installer, choose Minecraft <b>1.21.11</b> and press Install.', version: true },
      { title: 'Add the mod file', body: 'Put the PVPTraining jar in your <b>mods</b> folder. If you use the standard Minecraft Launcher we can do that for you below.', jar: true, targets: true },
      { title: 'Launch Fabric 1.21.11', body: 'Start the Fabric 1.21.11 installation, wait for the title screen, then come back here and press <b>Connect</b>.' },
    ],
    note: 'PVPTraining carries Fabric API inside it, so the one jar is all you need.',
  },
};

export const BRAND_ORDER = ['dawn', 'lunar', 'modrinth', 'other'];
