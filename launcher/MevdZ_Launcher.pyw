import os
import sys
import json
import time
import uuid
import queue
import shutil
import zipfile
import threading
import traceback
import subprocess
import urllib.request
import tkinter as tk
from tkinter import ttk, messagebox

CREATE_NO_WINDOW = 0x08000000 if os.name == "nt" else 0


def ensure_lib():
    try:
        import minecraft_launcher_lib  # noqa
        return True
    except ImportError:
        pass
    if getattr(sys, "frozen", False):
        return False  # exe'de kütüphane içine gömülü olmalı; pip denenmez
    exe = sys.executable
    if exe.lower().endswith("pythonw.exe"):
        exe = exe[:-len("pythonw.exe")] + "python.exe"
    try:
        subprocess.check_call(
            [exe, "-m", "pip", "install", "minecraft-launcher-lib"],
            creationflags=CREATE_NO_WINDOW,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        import minecraft_launcher_lib  # noqa
        return True
    except Exception:
        return False


if not ensure_lib():
    r = tk.Tk()
    r.withdraw()
    messagebox.showerror(
        "MevdZ Launcher",
        "Gerekli kütüphane kurulamadı.\nİnternet bağlantını kontrol et ve tekrar dene.",
    )
    sys.exit(1)

import minecraft_launcher_lib as mll  # noqa: E402

APP_NAME = "MevdZ Launcher"
FROZEN = getattr(sys, "frozen", False)
SCRIPT_DIR = (os.path.dirname(sys.executable) if FROZEN
              else os.path.dirname(os.path.abspath(__file__)))
# exe içine gömülü mod jar'ının klasörü (PyInstaller --add-data "jar;mod")
BUNDLE_MOD_DIR = os.path.join(sys._MEIPASS, "mod") if FROZEN else None
BASE_DIR = os.path.join(os.getenv("APPDATA") or os.path.expanduser("~"), ".mevdz")
GAME_DIR = os.path.join(BASE_DIR, "game")
CONFIG = os.path.join(BASE_DIR, "config.json")
LOG_FILE = os.path.join(BASE_DIR, "son_log.txt")          # oyunun logu
LAUNCHER_LOG = os.path.join(BASE_DIR, "launcher_log.txt")  # launcher logu
os.makedirs(GAME_DIR, exist_ok=True)

FORGE_VER = "1.8.9-11.15.1.2318-1.8.9"
FORGE_INSTALLER_URL = ("https://maven.minecraftforge.net/net/minecraftforge/forge/"
                       f"{FORGE_VER}/forge-{FORGE_VER}-installer.jar")
LIB_MIRRORS = ["https://maven.minecraftforge.net/",
               "https://libraries.minecraft.net/",
               "https://repo1.maven.org/maven2/"]

W, H = 460, 660

BG = "#090A10"
PANEL = "#12141E"
LINE = "#222637"
TEXT = "#EAF0FF"
MUTED = "#6F7896"
CYAN = "#19C3FF"
VIOLET = "#7C5CFF"
OFF = "#262B3E"

READY_MARKERS = ("MevdZ_READY", "Sound engine started", "OpenAL initialized",
                 "SoundSystem started")


def log(msg):
    try:
        with open(LAUNCHER_LOG, "a", encoding="utf-8") as f:
            f.write(time.strftime("%H:%M:%S ") + str(msg) + "\n")
    except Exception:
        pass


def lerp(c1, c2, t):
    a = [int(c1[i:i + 2], 16) for i in (1, 3, 5)]
    b = [int(c2[i:i + 2], 16) for i in (1, 3, 5)]
    return "#%02x%02x%02x" % tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def grad_h(cv, x0, y0, x1, y1, c1, c2, tag=""):
    n = max(1, int(x1 - x0))
    for i in range(n):
        cv.create_line(x0 + i, y0, x0 + i, y1, fill=lerp(c1, c2, i / n), tags=tag)


def load_cfg():
    try:
        with open(CONFIG, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def save_cfg(d):
    try:
        with open(CONFIG, "w", encoding="utf-8") as f:
            json.dump(d, f)
    except Exception:
        pass


# ---------------------------------------------------------------- widgetlar
class GradButton(tk.Canvas):
    def __init__(self, master, text, command, width=388, height=54):
        super().__init__(master, width=width, height=height, bg=BG,
                         highlightthickness=0, cursor="hand2")
        self.w, self.h, self.text, self.command = width, height, text, command
        self.hover = False
        self.enabled = True
        self.bind("<Enter>", lambda e: self._set_hover(True))
        self.bind("<Leave>", lambda e: self._set_hover(False))
        self.bind("<Button-1>", self._click)
        self.draw()

    def _set_hover(self, v):
        self.hover = v
        self.draw()

    def _click(self, _):
        if self.enabled:
            self.command()

    def configure_state(self, text, enabled):
        self.text, self.enabled = text, enabled
        self.config(cursor="hand2" if enabled else "arrow")
        self.draw()

    def draw(self):
        self.delete("all")
        if self.enabled:
            c1, c2 = CYAN, VIOLET
            if self.hover:
                c1, c2 = lerp(c1, "#FFFFFF", 0.18), lerp(c2, "#FFFFFF", 0.18)
            grad_h(self, 0, 0, self.w, self.h, lerp(c1, BG, 0.55), lerp(c2, BG, 0.55))
            grad_h(self, 2, 2, self.w - 2, self.h - 2, c1, c2)
        else:
            self.create_rectangle(0, 0, self.w, self.h, fill=OFF, outline="")
        self.create_text(self.w / 2, self.h / 2, text=self.text,
                         fill="#FFFFFF" if self.enabled else MUTED,
                         font=("Segoe UI", 13, "bold"))


class GradBar(tk.Canvas):
    def __init__(self, master, width=388, height=6):
        super().__init__(master, width=width, height=height, bg=BG,
                         highlightthickness=0)
        self.w, self.h = width, height
        self.set(0)

    def set(self, pct):
        self.delete("all")
        self.create_rectangle(0, 0, self.w, self.h, fill=PANEL, outline="")
        x = int(self.w * max(0, min(100, pct)) / 100)
        if x > 0:
            grad_h(self, 0, 0, x, self.h, CYAN, VIOLET)


class Splash(tk.Canvas):
    """Oyun açılmadan önce gösterilen yükleme ekranı."""

    def __init__(self, master):
        super().__init__(master, bg=BG, highlightthickness=0)
        self.angle = 0
        self.running = False
        cx, cy = W // 2, 235
        self.create_oval(cx - 56, cy - 56, cx + 56, cy + 56, outline=LINE, width=4)
        self.arc1 = self.create_arc(cx - 56, cy - 56, cx + 56, cy + 56, start=0,
                                    extent=100, style="arc", outline=CYAN, width=4)
        self.create_oval(cx - 38, cy - 38, cx + 38, cy + 38, outline=PANEL, width=3)
        self.arc2 = self.create_arc(cx - 38, cy - 38, cx + 38, cy + 38, start=180,
                                    extent=80, style="arc", outline=VIOLET, width=3)
        self.create_text(cx, 345, text="MevdZ", fill=TEXT,
                         font=("Segoe UI", 26, "bold"))
        self.create_text(cx, 380, text="L A U N C H E R", fill=CYAN,
                         font=("Segoe UI", 10, "bold"))
        self.msg = self.create_text(cx, 450, text="Hazırlanıyor...", fill=MUTED,
                                    font=("Segoe UI", 10), width=380,
                                    justify="center")
        self.pct = self.create_text(cx, 495, text="", fill=TEXT,
                                    font=("Segoe UI", 12, "bold"))

    def show(self):
        self.place(x=0, y=0, relwidth=1, relheight=1)
        tk.Misc.tkraise(self)  # Canvas.tkraise öğe seviyesindedir, widget için bunu kullan
        self.running = True
        self.tick()

    def hide(self):
        self.running = False
        self.place_forget()

    def set_msg(self, t):
        self.itemconfig(self.msg, text=t)

    def set_pct(self, p):
        self.itemconfig(self.pct, text="" if p is None else f"%{int(p)}")

    def tick(self):
        if not self.running:
            return
        self.angle = (self.angle + 7) % 360
        self.itemconfig(self.arc1, start=self.angle)
        self.itemconfig(self.arc2, start=(-self.angle * 1.6) % 360)
        self.after(20, self.tick)


# ---------------------------------------------------------------- uygulama
class App(tk.Tk):
    def __init__(self):
        super().__init__()
        try:
            if os.path.isfile(LAUNCHER_LOG) and os.path.getsize(LAUNCHER_LOG) > 300000:
                os.remove(LAUNCHER_LOG)
        except Exception:
            pass
        log("======== launcher açıldı ========")
        self.title(APP_NAME)
        self.configure(bg=BG)
        self.geometry(f"{W}x{H}")
        self.resizable(False, False)
        self.cfg = load_cfg()
        self.all_versions = []
        self.busy = False
        self.note = ""
        self._last_pct = -1
        self.q = queue.Queue()   # worker thread -> arayüz mesajları

        self.setup_style()
        self.build_ui()
        self.splash = Splash(self)
        self.after(40, self.pump)
        self.after(120, self.dark_titlebar)
        threading.Thread(target=self.fetch_versions, daemon=True).start()

    def report_callback_exception(self, exc, val, tb):
        log("ARAYÜZ HATASI:\n" + "".join(traceback.format_exception(exc, val, tb)))

    # thread güvenli arayüz köprüsü
    def ui(self, fn):
        self.q.put(fn)

    def pump(self):
        try:
            for _ in range(300):
                fn = self.q.get_nowait()
                try:
                    fn()
                except Exception:
                    log(traceback.format_exc())
        except queue.Empty:
            pass
        self.after(40, self.pump)

    def dark_titlebar(self):
        try:
            import ctypes
            self.update()
            hwnd = ctypes.windll.user32.GetParent(self.winfo_id())
            v = ctypes.c_int(1)
            for attr in (20, 19):
                ctypes.windll.dwmapi.DwmSetWindowAttribute(
                    hwnd, attr, ctypes.byref(v), 4)
        except Exception:
            pass

    def setup_style(self):
        s = ttk.Style(self)
        s.theme_use("clam")
        s.configure("M.TCombobox", fieldbackground=PANEL, background=PANEL,
                    foreground=TEXT, bordercolor=LINE, lightcolor=PANEL,
                    darkcolor=PANEL, arrowcolor=CYAN, padding=8,
                    selectbackground=PANEL, selectforeground=TEXT)
        s.map("M.TCombobox",
              fieldbackground=[("readonly", PANEL)],
              foreground=[("readonly", TEXT)],
              selectbackground=[("readonly", PANEL)],
              selectforeground=[("readonly", TEXT)],
              bordercolor=[("focus", CYAN)])
        s.configure("M.Horizontal.TScale", background=CYAN, troughcolor=LINE,
                    bordercolor=BG, lightcolor=CYAN, darkcolor=CYAN)
        self.option_add("*TCombobox*Listbox.background", PANEL)
        self.option_add("*TCombobox*Listbox.foreground", TEXT)
        self.option_add("*TCombobox*Listbox.selectBackground", VIOLET)
        self.option_add("*TCombobox*Listbox.selectForeground", "#FFFFFF")
        self.option_add("*TCombobox*Listbox.font", ("Segoe UI", 10))

    def build_ui(self):
        top = tk.Canvas(self, width=W, height=3, bg=BG, highlightthickness=0)
        top.pack(fill="x")
        grad_h(top, 0, 0, W, 3, CYAN, VIOLET)

        head = tk.Frame(self, bg=BG)
        head.pack(fill="x", padx=36, pady=(34, 0))
        tk.Label(head, text="MevdZ", font=("Segoe UI", 36, "bold"),
                 bg=BG, fg=TEXT).pack(anchor="w")
        tk.Label(head, text="L A U N C H E R", font=("Segoe UI", 11, "bold"),
                 bg=BG, fg=CYAN).pack(anchor="w")

        line = tk.Canvas(self, width=388, height=2, bg=BG, highlightthickness=0)
        line.pack(padx=36, pady=(22, 22))
        grad_h(line, 0, 0, 388, 2, CYAN, BG)

        body = tk.Frame(self, bg=BG)
        body.pack(fill="x", padx=36)

        tk.Label(body, text="OYUNCU ADI", font=("Segoe UI", 8, "bold"),
                 bg=BG, fg=MUTED).pack(anchor="w")
        wrap = tk.Frame(body, bg=PANEL, highlightthickness=1,
                        highlightbackground=LINE, highlightcolor=CYAN)
        wrap.pack(fill="x", pady=(6, 18))
        self.name_var = tk.StringVar(value=self.cfg.get("name", ""))
        tk.Entry(wrap, textvariable=self.name_var, font=("Segoe UI", 12),
                 bg=PANEL, fg=TEXT, insertbackground=CYAN, relief="flat",
                 bd=0).pack(fill="x", padx=12, pady=11)

        tk.Label(body, text="SÜRÜM", font=("Segoe UI", 8, "bold"),
                 bg=BG, fg=MUTED).pack(anchor="w")
        self.ver_var = tk.StringVar(value="Sürümler yükleniyor...")
        self.combo = ttk.Combobox(body, textvariable=self.ver_var,
                                  state="readonly", font=("Segoe UI", 11),
                                  style="M.TCombobox")
        self.combo.pack(fill="x", pady=(6, 8), ipady=4)

        self.snap_var = tk.BooleanVar(value=self.cfg.get("snap", False))
        self._check(body, "Snapshot / eski sürümleri göster", self.snap_var,
                    self.refresh_list).pack(anchor="w")

        self.client_var = tk.BooleanVar(value=self.cfg.get("client", True))
        self._check(body, "MevdZ Client modu (Forge, sadece 1.8.9)",
                    self.client_var, None).pack(anchor="w", pady=(0, 18))

        tk.Label(body, text="RAM", font=("Segoe UI", 8, "bold"),
                 bg=BG, fg=MUTED).pack(anchor="w")
        row = tk.Frame(body, bg=BG)
        row.pack(fill="x", pady=(6, 0))
        self.ram_var = tk.DoubleVar(value=float(self.cfg.get("ram", 2)))
        self.ram_lbl = tk.Label(row, text=f"{int(self.ram_var.get())} GB",
                                font=("Segoe UI", 11, "bold"), bg=BG, fg=TEXT,
                                width=6, anchor="e")
        self.ram_lbl.pack(side="right")
        ttk.Scale(row, from_=1, to=16, orient="horizontal",
                  variable=self.ram_var, style="M.Horizontal.TScale",
                  command=lambda v: self.ram_lbl.config(
                      text=f"{int(round(float(v)))} GB")
                  ).pack(side="left", fill="x", expand=True)

        self.btn = GradButton(self, "OYNA", self.on_play)
        self.btn.pack(padx=36, pady=(30, 0))

        self.bar = GradBar(self)
        self.bar.pack(padx=36, pady=(24, 8))
        self.status = tk.Label(self, text="Hazır", font=("Segoe UI", 9),
                               bg=BG, fg=MUTED, anchor="w")
        self.status.pack(fill="x", padx=36)

    def _check(self, parent, text, var, cmd):
        return tk.Checkbutton(parent, text=text, variable=var, command=cmd,
                              font=("Segoe UI", 9), bg=BG, fg=MUTED,
                              activebackground=BG, activeforeground=TEXT,
                              selectcolor=PANEL, bd=0, highlightthickness=0)

    # ---------- sürümler ----------
    def fetch_versions(self):
        try:
            self.all_versions = mll.utils.get_version_list()
        except Exception:
            log(traceback.format_exc())
            self.all_versions = []
        self.ui(self.refresh_list)

    def refresh_list(self):
        if not self.all_versions:
            self.ver_var.set("Sürümler alınamadı (internet?)")
            return
        show_all = self.snap_var.get()
        ids = [v["id"] for v in self.all_versions
               if show_all or v["type"] == "release"]
        self.combo["values"] = ids
        last = self.cfg.get("version")
        if self.ver_var.get() in ids:
            pass
        elif last in ids:
            self.ver_var.set(last)
        elif ids:
            self.ver_var.set(ids[0])

    # ---------- durum (her thread'den çağrılabilir) ----------
    def set_status(self, text):
        log(text)

        def f():
            self.status.config(text=text)
            self.splash.set_msg(text)
        self.ui(f)

    def set_pct(self, pct):
        if pct is not None:
            p = int(pct)
            if p == self._last_pct:
                return
            self._last_pct = p

        def f():
            self.bar.set(100 if pct is None else pct)
            self.splash.set_pct(pct)
        self.ui(f)

    # ---------- oyun ----------
    def on_play(self):
        if self.busy:
            return
        name = self.name_var.get().strip()
        ver = self.ver_var.get().strip()
        if (not (3 <= len(name) <= 16)
                or not all(c.isalnum() or c == "_" for c in name)):
            messagebox.showwarning(
                APP_NAME, "İsim 3-16 karakter olmalı; sadece harf, rakam ve _ kullan.")
            return
        if ver not in list(self.combo["values"]):
            messagebox.showwarning(APP_NAME, "Önce bir sürüm seç.")
            return
        self.cfg.update({"name": name, "version": ver,
                         "ram": int(round(self.ram_var.get())),
                         "snap": self.snap_var.get(),
                         "client": self.client_var.get()})
        save_cfg(self.cfg)
        log(f"OYNA basıldı: isim={name} sürüm={ver} client={self.cfg.get('client')}")
        self.busy = True
        self.note = ""
        self._last_pct = -1
        self.btn.configure_state("HAZIRLANIYOR...", False)
        self.bar.set(0)
        self.splash.set_pct(0)
        self.splash.set_msg("Hazırlanıyor...")
        self.splash.show()
        threading.Thread(target=self.launch, args=(name, ver), daemon=True).start()

    def finish(self, error):
        self.splash.hide()
        self.busy = False
        self.btn.configure_state("OYNA", True)
        if error:
            self.status.config(text="Hata oluştu")
            messagebox.showerror(APP_NAME, error)
        else:
            self.status.config(text="Oyun açıldı. İyi eğlenceler!")
            if self.note:
                messagebox.showinfo(APP_NAME, self.note)

    def launch(self, name, ver):
        error = None
        try:
            error = self._launch(name, ver)
        except Exception as e:
            log(traceback.format_exc())
            error = f"Başlatılamadı:\n{e}\n\nDetay: {LAUNCHER_LOG}"
        self.ui(lambda: self.finish(error))

    def make_cb(self):
        state = {"max": 1}

        def set_max(v):
            state["max"] = max(int(v), 1)

        def set_progress(v):
            self.set_pct(min(100, int(v) * 100 / state["max"]))

        return {"setStatus": self.set_status, "setProgress": set_progress,
                "setMax": set_max}

    def _launch(self, name, ver):
        log("başlatma thread'i çalıştı")
        cb = self.make_cb()
        use_client = bool(self.cfg.get("client")) and ver == "1.8.9"

        self.set_status("Mojang sunucusuna bağlanılıyor...")
        try:
            urllib.request.urlopen(urllib.request.Request(
                "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json",
                headers={"User-Agent": "MevdZ-Launcher/3.0"}), timeout=15).read(100)
            log("Mojang erişimi OK")
        except Exception as e:
            log("Mojang erişimi BAŞARISIZ: " + repr(e))
            return ("Mojang sunucusuna ulaşılamadı.\n"
                    "İnternetini, VPN/antivirüs/güvenlik duvarını kontrol et.\n\n"
                    f"Hata: {e}")

        self.set_status("Dosyalar indiriliyor / kontrol ediliyor...")
        mll.install.install_minecraft_version(ver, GAME_DIR, callback=cb)

        launch_id = ver
        if use_client:
            self.set_status("MevdZ Client (Forge) hazırlanıyor...")
            launch_id = self.ensure_forge()
            if not self.copy_mod():
                self.note = ("MevdZ Client modu (jar dosyası) bulunamadı.\n"
                             "Oyun Forge ile açıldı ama oyun içi menü/HUD yok.\n\n"
                             "MevdZClient-....jar dosyasını launcher'ın yanına "
                             "veya şu klasöre koy:\n" + os.path.join(GAME_DIR, "mods"))

        self.fix_natives(ver, launch_id)
        java = self.ensure_java(ver, cb)

        ram = int(self.cfg["ram"])
        options = {
            "username": name,
            "uuid": str(uuid.uuid3(uuid.NAMESPACE_DNS, "OfflinePlayer:" + name)),
            "token": "0",
            "jvmArguments": [f"-Xmx{ram}G", f"-Xms{max(1, ram // 2)}G"],
            "launcherName": APP_NAME,
            "launcherVersion": "3.0",
        }
        if java:
            options["executablePath"] = java
        cmd = mll.command.get_minecraft_command(launch_id, GAME_DIR, options)
        if "--versionType" in cmd:
            cmd[cmd.index("--versionType") + 1] = APP_NAME
        log("KOMUT: " + " ".join(cmd)[:600])

        self.set_status("Oyun başlatılıyor...")
        self.set_pct(None)
        proc = subprocess.Popen(
            cmd, cwd=GAME_DIR, stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding="utf-8", errors="replace",
            creationflags=CREATE_NO_WINDOW)

        ready = threading.Event()

        def reader():
            try:
                with open(LOG_FILE, "w", encoding="utf-8") as lf:
                    for line in proc.stdout:
                        lf.write(line)
                        lf.flush()
                        if not ready.is_set() and any(m in line for m in READY_MARKERS):
                            ready.set()
            except Exception:
                pass

        threading.Thread(target=reader, daemon=True).start()

        self.set_status("Oyun yükleniyor, bir an...")
        t0 = time.time()
        while not ready.is_set():
            if proc.poll() is not None:
                break
            if time.time() - t0 > 180:
                break
            time.sleep(0.3)

        if not ready.is_set() and proc.poll() is not None:
            return (f"Oyun beklenmedik şekilde kapandı (kod {proc.returncode}).\n"
                    f"Detay için şu dosyaya bak:\n{LOG_FILE}")
        time.sleep(1.5)
        return None

    # ---------- Java ----------
    def ensure_java(self, ver, cb):
        """Mojang'ın kendi Java'sını kullan (1.8.9 için jre-legacy)."""
        try:
            comp = "jre-legacy"
            jf = os.path.join(GAME_DIR, "versions", ver, ver + ".json")
            try:
                with open(jf, "r", encoding="utf-8") as f:
                    comp = json.load(f).get("javaVersion", {}).get("component", comp)
            except Exception:
                pass
            exe = mll.runtime.get_executable_path(comp, GAME_DIR)
            if not exe:
                self.set_status("Java indiriliyor...")
                mll.runtime.install_jvm_runtime(comp, GAME_DIR, callback=cb)
                exe = mll.runtime.get_executable_path(comp, GAME_DIR)
            return exe
        except Exception:
            log("Java hazırlanamadı:\n" + traceback.format_exc())
            return None

    # ---------- Forge 1.8.9 ----------
    @staticmethod
    def maven_path(name):
        p = name.split(":")
        g, a, v = p[0], p[1], p[2]
        cl = f"-{p[3]}" if len(p) > 3 else ""
        return f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}{cl}.jar"

    @staticmethod
    def download(url, dest):
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        req = urllib.request.Request(url, headers={"User-Agent": "MevdZ-Launcher/3.0"})
        with urllib.request.urlopen(req, timeout=60) as r, open(dest + ".part", "wb") as f:
            shutil.copyfileobj(r, f)
        os.replace(dest + ".part", dest)

    @staticmethod
    def find_forge_id():
        vd = os.path.join(GAME_DIR, "versions")
        if os.path.isdir(vd):
            for n in sorted(os.listdir(vd)):
                if (n.startswith("1.8.9") and "forge" in n.lower()
                        and os.path.isfile(os.path.join(vd, n, n + ".json"))):
                    return n
        return None

    def ensure_forge(self):
        fid = self.find_forge_id()
        if fid:
            return fid
        self.set_status("Forge 1.8.9 indiriliyor...")
        installer = os.path.join(BASE_DIR, "forge-1.8.9-installer.jar")
        self.download(FORGE_INSTALLER_URL, installer)
        with zipfile.ZipFile(installer) as z:
            prof = json.loads(z.read("install_profile.json").decode("utf-8"))
            vi = prof["versionInfo"]
            vid = vi["id"]
            inst = prof.get("install", {})
            fpath, lpath = inst.get("filePath"), inst.get("path")
            if fpath and lpath:
                dest = os.path.join(GAME_DIR, "libraries", *self.maven_path(lpath).split("/"))
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                with open(dest, "wb") as f:
                    f.write(z.read(fpath))

            libs = [l for l in vi.get("libraries", [])
                    if not l["name"].startswith("net.minecraftforge:forge:")
                    and l.get("clientreq", True) is not False]
            self.set_status("Forge kütüphaneleri indiriliyor...")
            for i, lib in enumerate(libs):
                path = self.maven_path(lib["name"])
                dest = os.path.join(GAME_DIR, "libraries", *path.split("/"))
                self.set_pct(i * 100 / max(1, len(libs)))
                if os.path.isfile(dest):
                    continue
                bases = []
                u = lib.get("url")
                if u:
                    u = u.replace("http://files.minecraftforge.net/maven/",
                                  "https://maven.minecraftforge.net/")
                    bases.append(u.replace("http://", "https://"))
                bases += LIB_MIRRORS
                ok = False
                for b in dict.fromkeys(bases):
                    try:
                        self.download(b.rstrip("/") + "/" + path, dest)
                        ok = True
                        break
                    except Exception:
                        continue
                if not ok:
                    raise RuntimeError("Forge kütüphanesi indirilemedi: " + lib["name"])

            vdir = os.path.join(GAME_DIR, "versions", vid)
            os.makedirs(vdir, exist_ok=True)
            with open(os.path.join(vdir, vid + ".json"), "w", encoding="utf-8") as f:
                json.dump(vi, f, indent=2)
        return vid

    def fix_natives(self, base_id, launch_id):
        """Forge'un natives klasörü boşsa (lwjgl64 hatası) vanilla'dan kopyala."""
        if base_id == launch_id:
            return
        s = os.path.join(GAME_DIR, "versions", base_id, "natives")
        d = os.path.join(GAME_DIR, "versions", launch_id, "natives")
        try:
            if not os.path.isdir(s):
                log("natives kaynağı yok: " + s)
                return
            os.makedirs(d, exist_ok=True)
            for n in os.listdir(s):
                sp, dp = os.path.join(s, n), os.path.join(d, n)
                if os.path.isfile(sp) and not os.path.isfile(dp):
                    shutil.copy2(sp, dp)
            log("natives kopyalandı: " + d)
        except Exception as e:
            log("natives kopyalanamadı: " + repr(e))

    def copy_mod(self):
        mods = os.path.join(GAME_DIR, "mods")
        os.makedirs(mods, exist_ok=True)
        best = None
        if BUNDLE_MOD_DIR and os.path.isdir(BUNDLE_MOD_DIR):
            dirs = [BUNDLE_MOD_DIR]
        else:
            dirs = [SCRIPT_DIR, BASE_DIR]
        for d in dirs:
            try:
                for n in os.listdir(d):
                    l = n.lower()
                    if (l.startswith("mevdz") and l.endswith(".jar")
                            and "launcher" not in l and "sources" not in l):
                        p = os.path.join(d, n)
                        if best is None or os.path.getmtime(p) > os.path.getmtime(best):
                            best = p
            except Exception:
                pass
        if best:
            for n in os.listdir(mods):
                if n.lower().startswith("mevdz") and n.lower().endswith(".jar"):
                    try:
                        os.remove(os.path.join(mods, n))
                    except Exception:
                        pass
            shutil.copy2(best, os.path.join(mods, os.path.basename(best)))
            log("Mod kopyalandı: " + best)
        return any(n.lower().startswith("mevdz") and n.lower().endswith(".jar")
                   for n in os.listdir(mods))


if __name__ == "__main__":
    App().mainloop()
