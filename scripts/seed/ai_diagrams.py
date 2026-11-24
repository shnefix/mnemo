#!/usr/bin/env python3
"""
Draws the diagrams of the built-in "AI & ML" deck (section "7. Diagrams") and writes, for each,
an image and its image-occlusion boxes:

  composeApp/src/commonMain/composeResources/files/seed/ai/<name>.webp
  composeApp/src/commonMain/kotlin/app/mnemo/data/seed/AiDiagrams.kt   (generated)

Every label box is one card ("hide one, guess one"). Run from the repository root:
  python3 scripts/seed/ai_diagrams.py
"""
import json
import math
import os

from PIL import Image, ImageDraw, ImageFont

W, H = 960, 840
BG = (250, 249, 246)
INK = (34, 36, 42)
MUTED = (110, 114, 124)
LINE = (150, 154, 162)
FILLS = [(255, 228, 214), (220, 236, 255), (224, 245, 230), (243, 232, 255), (255, 243, 205), (230, 240, 240)]
EDGES = [(232, 120, 84), (70, 130, 210), (70, 160, 100), (140, 100, 200), (210, 160, 40), (80, 140, 140)]
FONT_DIR = "/usr/share/fonts/google-noto"
OUT_IMG = "composeApp/src/commonMain/composeResources/files/seed/ai"
OUT_KT = "composeApp/src/commonMain/kotlin/app/mnemo/data/seed/AiDiagrams.kt"


def font(size, weight="SemiBold"):
    return ImageFont.truetype(os.path.join(FONT_DIR, f"NotoSans-{weight}.ttf"), size)


class Diagram:
    def __init__(self, name, header, extra, w=W, h=H):
        self.name, self.header, self.extra = name, header, extra
        self.w, self.h = w, h
        self.img = Image.new("RGB", (w, h), BG)
        self.d = ImageDraw.Draw(self.img)
        self.masks = []

    def text_center(self, box, text, size=28, color=INK, weight="SemiBold"):
        f = font(size, weight)
        lines = text.split("\n")
        heights = [self.d.textbbox((0, 0), ln, font=f)[3] for ln in lines]
        total = sum(heights) + 6 * (len(lines) - 1)
        x0, y0, x1, y1 = box
        y = (y0 + y1 - total) / 2
        for ln, hgt in zip(lines, heights):
            tw = self.d.textlength(ln, font=f)
            self.d.text(((x0 + x1 - tw) / 2, y), ln, font=f, fill=color)
            y += hgt + 6

    def box(self, box, text, i=0, size=28, mask=True, radius=18):
        self.d.rounded_rectangle(box, radius=radius, fill=FILLS[i % len(FILLS)], outline=EDGES[i % len(EDGES)], width=3)
        self.text_center(box, text, size)
        if mask:
            self.mask(box, text)

    def label(self, xy, text, size=26, color=INK, anchor="la", mask=True, pad=8, weight="SemiBold"):
        """Text placed by a two-letter anchor (l/m/r, then t/m/b; "a" counts as top), centred line by line."""
        f = font(size, weight)
        l, t, r, b = self.d.multiline_textbbox((0, 0), text, font=f, spacing=6, align="center")
        w, h = r - l, b - t
        hx, vy = anchor[0], anchor[1]
        x = xy[0] - {"l": 0, "m": w / 2, "r": w}[hx] - l
        y = xy[1] - {"a": 0, "t": 0, "m": h / 2, "b": h, "d": h}[vy] - t
        self.d.multiline_text((x, y), text, font=f, fill=color, spacing=6, align="center")
        if mask:
            self.mask((x + l - pad, y + t - pad, x + r + pad, y + b + pad), text)

    def mask(self, box, answer):
        x0, y0, x1, y1 = box
        self.masks.append(
            dict(x=round(x0 / self.w, 4), y=round(y0 / self.h, 4), w=round((x1 - x0) / self.w, 4), h=round((y1 - y0) / self.h, 4),
                 group=len(self.masks) + 1, answer=answer.replace("\n", " "))
        )

    def arrow(self, a, b, color=LINE, width=4, head=14):
        self.d.line([a, b], fill=color, width=width)
        ang = math.atan2(b[1] - a[1], b[0] - a[0])
        p1 = (b[0] - head * math.cos(ang - 0.45), b[1] - head * math.sin(ang - 0.45))
        p2 = (b[0] - head * math.cos(ang + 0.45), b[1] - head * math.sin(ang + 0.45))
        self.d.polygon([b, p1, p2], fill=color)

    def title(self, text):
        self.d.text((40, 28), text, font=font(30, "Bold"), fill=MUTED)

    def save(self):
        os.makedirs(OUT_IMG, exist_ok=True)
        self.img.save(os.path.join(OUT_IMG, self.name + ".webp"), "WEBP", quality=88, method=6)


def flow(name, title, header, extra, labels, cols=2, loop=False):
    """Boxes in a snake: left to right, then right to left on the next row, joined by arrows."""
    g = Diagram(name, header, extra)
    g.title(title)
    rows = math.ceil(len(labels) / cols)
    top, bottom, side, gap_x = 100, 40, 50, 90
    bw = (W - 2 * side - gap_x * (cols - 1)) / cols
    gap_y = 70
    bh = min(130, (H - top - bottom - gap_y * (rows - 1)) / rows)
    gap_y = (H - top - bottom - bh * rows) / max(1, rows - 1) if rows > 1 else 0
    centers = []
    for i, text in enumerate(labels):
        r, c = divmod(i, cols)
        if r % 2 == 1:
            c = cols - 1 - c
        x0 = side + c * (bw + gap_x)
        y0 = top + r * (bh + gap_y)
        g.box((x0, y0, x0 + bw, y0 + bh), text, i)
        centers.append((x0, y0, x0 + bw, y0 + bh))
    for a, b in zip(centers, centers[1:]):
        ax, ay = (a[0] + a[2]) / 2, (a[1] + a[3]) / 2
        bx, by = (b[0] + b[2]) / 2, (b[1] + b[3]) / 2
        if abs(ay - by) < 1:  # same row
            if bx > ax:
                g.arrow((a[2] + 6, ay), (b[0] - 6, by))
            else:
                g.arrow((a[0] - 6, ay), (b[2] + 6, by))
        else:
            g.arrow((ax, a[3] + 6), (bx, b[1] - 6))
    if loop and len(centers) > 2:
        a, b = centers[-1], centers[0]
        x = side / 2 - 5
        ay, by = (a[1] + a[3]) / 2, (b[1] + b[3]) / 2
        g.d.line([(a[0] - 6, ay), (x, ay), (x, by)], fill=LINE, width=4)
        g.arrow((x, by), (b[0] - 6, by))
    g.save()
    return g


def transformer():
    g = Diagram(
        "transformer-block",
        "A decoder-only Transformer (GPT-style), bottom to top. Name the hidden part.",
        "Each block = masked self-attention + feed-forward, each wrapped in a residual connection and layer norm. "
        "GPT models stack dozens of blocks; the last layer turns each position into next-token probabilities.",
        h=900,
    )
    g.title("Transformer (decoder-only)")
    labels = [
        "Next-token probabilities",
        "Softmax",
        "Linear (to vocabulary)",
        "Add & Norm",
        "Feed-forward (MLP)",
        "Add & Norm",
        "Masked multi-head self-attention",
        "Positional encoding",
        "Token embeddings",
    ]
    x0, x1, top, bh, gap = 230, 730, 90, 64, 26
    boxes = []
    for i, text in enumerate(labels):
        y0 = top + i * (bh + gap)
        idx = {0: 3, 1: 3, 2: 3, 3: 0, 4: 1, 5: 0, 6: 2, 7: 4, 8: 4}[i]
        g.box((x0, y0, x1, y0 + bh), text, idx, size=26)
        boxes.append((x0, y0, x1, y0 + bh))
    for a, b in zip(boxes[1:], boxes):
        g.arrow(((a[0] + a[2]) / 2, a[1] - 2), ((b[0] + b[2]) / 2, b[3] + 4))
    # residual connections around attention and feed-forward
    for lo, hi in ((6, 5), (4, 3)):
        a, b = boxes[lo], boxes[hi]
        x = x1 + 40
        g.d.line([(x1, (a[1] + a[3]) / 2 + 20), (x, (a[1] + a[3]) / 2 + 20), (x, (b[1] + b[3]) / 2)], fill=EDGES[0], width=3)
        g.arrow((x, (b[1] + b[3]) / 2), (x1 + 4, (b[1] + b[3]) / 2), color=EDGES[0], width=3)
    g.label((x1 + 60, (boxes[3][1] + boxes[6][3]) / 2), "Residual\nconnections", size=24, anchor="lm")
    # the repeated block bracket
    yb0, yb1 = boxes[3][1] - 10, boxes[6][3] + 10
    g.d.rounded_rectangle((x0 - 30, yb0, x1 + 30, yb1), radius=22, outline=MUTED, width=2)
    g.label((x0 - 45, (yb0 + yb1) / 2), "× N\nlayers", size=26, anchor="rm")
    g.save()
    return g


def confusion():
    g = Diagram(
        "confusion-matrix",
        "A confusion matrix for a yes/no classifier. Name the hidden cell or axis.",
        "Precision = TP / (TP + FP). Recall = TP / (TP + FN). Accuracy = (TP + TN) / everything. "
        "FP is a type I error (false alarm), FN a type II error (a miss).",
    )
    g.title("Confusion matrix")
    gx, gy, cw, ch = 330, 230, 270, 230
    g.label((gx + cw, 150), "Predicted", size=30, anchor="mm")
    g.label((gx + cw / 2, 205), "Positive", size=24, color=MUTED, anchor="mm", mask=False)
    g.label((gx + 1.5 * cw, 205), "Negative", size=24, color=MUTED, anchor="mm", mask=False)
    g.label((120, gy + ch), "Actual", size=30, anchor="mm")
    g.label((gx - 20, gy + ch / 2), "Positive", size=24, color=MUTED, anchor="rm", mask=False)
    g.label((gx - 20, gy + 1.5 * ch), "Negative", size=24, color=MUTED, anchor="rm", mask=False)
    cells = [
        ("True positive\n(TP)", 2, 0, 0),
        ("False negative\n(FN)", 0, 1, 0),
        ("False positive\n(FP)", 0, 0, 1),
        ("True negative\n(TN)", 2, 1, 1),
    ]
    for text, color, c, r in cells:
        box = (gx + c * cw + 8, gy + r * ch + 8, gx + (c + 1) * cw - 8, gy + (r + 1) * ch - 8)
        g.box(box, text, color, size=28)
    g.save()
    return g


def network():
    g = Diagram(
        "neural-network",
        "A small fully connected neural network. Name the hidden part.",
        "Each connection has a weight; each neuron adds its inputs times weights plus a bias, then applies an "
        "activation function (e.g. ReLU). Training adjusts weights and biases with backpropagation.",
    )
    g.title("Neural network (multilayer perceptron)")
    layers = [3, 5, 5, 2]
    xs = [170, 400, 630, 840]
    pos = []
    for n, x in zip(layers, xs):
        span = 470
        ys = [250 + span * (i + 0.5) / n for i in range(n)]
        pos.append([(x, y) for y in ys])
    for a, b in zip(pos, pos[1:]):
        for p in a:
            for q in b:
                g.d.line([p, q], fill=(200, 202, 208), width=2)
    for li, layer in enumerate(pos):
        for x, y in layer:
            g.d.ellipse((x - 26, y - 26, x + 26, y + 26), fill=FILLS[[1, 2, 2, 0][li]], outline=EDGES[[1, 2, 2, 0][li]], width=3)
    g.label((170, 175), "Input layer", size=26, anchor="mm")
    g.label((515, 175), "Hidden layers", size=26, anchor="mm")
    g.label((840, 175), "Output layer", size=26, anchor="mm")
    mid = ((pos[0][1][0] + pos[1][2][0]) / 2, (pos[0][1][1] + pos[1][2][1]) / 2)
    g.label((285, 790), "Weights\n(connections)", size=24, anchor="mm")
    g.arrow((285, 750), (mid[0], mid[1] + 40), color=MUTED, width=2, head=10)
    g.label((630, 790), "Neuron: activation(w·x + b)", size=24, anchor="mm")
    g.arrow((630, 765), (pos[2][4][0], pos[2][4][1] + 30), color=MUTED, width=2, head=10)
    g.save()
    return g


def fitting():
    g = Diagram(
        "bias-variance",
        "Training and validation error as the model gets more complex. Name the hidden label.",
        "Too simple: both errors high (underfitting, high bias). Too complex: training error keeps falling but "
        "validation error rises (overfitting, high variance). Pick the complexity where validation error is lowest.",
    )
    g.title("Underfitting vs overfitting")
    ox, oy, ex, ty = 120, 720, 900, 140
    g.arrow((ox, oy), (ex, oy), color=INK, width=3)
    g.arrow((ox, oy), (ox, ty), color=INK, width=3)
    g.label(((ox + ex) / 2, oy + 50), "Model complexity", size=26, anchor="mm")
    g.label((ox - 20, (oy + ty) / 2 + 40), "Error", size=26, anchor="rm")
    span_x, span_y = ex - ox - 60, oy - ty - 120

    def at(t, e):
        """Curve point at complexity t (0..1) with error e (0..1)."""
        return (ox + 20 + t * span_x, ty + 60 + span_y * (1 - e))

    ts = [i / 100 for i in range(101)]
    train = [at(t, 0.24 + 0.7 * math.exp(-3.4 * t)) for t in ts]
    val_e = [0.36 + 0.6 * math.exp(-4.5 * t) + 0.6 * t * t for t in ts]
    val = [at(t, min(0.98, e)) for t, e in zip(ts, val_e)]
    g.d.line(train, fill=EDGES[1], width=5, joint="curve")
    g.d.line(val, fill=EDGES[0], width=5, joint="curve")
    best = min(range(101), key=lambda i: val_e[i])
    bx, by = val[best]
    g.d.line([(bx, ty + 20), (bx, oy)], fill=MUTED, width=2)
    g.label((bx, ty + 10), "Sweet spot", size=24, anchor="mb")
    g.label((train[75][0], train[75][1] + 16), "Training error", size=24, color=EDGES[1], anchor="mt")
    g.label((val[80][0] - 24, val[80][1]), "Validation error", size=24, color=EDGES[0], anchor="rb")
    g.label((ox + 150, oy - 30), "Underfitting\n(high bias)", size=24, anchor="mb")
    g.label((ex - 150, oy - 30), "Overfitting\n(high variance)", size=24, anchor="mb")
    g.save()
    return g


def roc():
    g = Diagram(
        "roc-curve",
        "A ROC curve. Name the hidden label.",
        "The ROC curve plots recall (true positive rate) against the false positive rate as you move the decision "
        "threshold. AUC = 1.0 is perfect, 0.5 is guessing. With rare positives, a precision-recall curve is often clearer.",
    )
    g.title("ROC curve")
    ox, oy, s = 270, 730, 580
    g.d.rectangle((ox, oy - s, ox + s, oy), outline=INK, width=3)
    pts = [(ox + s * t, oy - s * (1 - (1 - t) ** 3.2)) for t in [i / 100 for i in range(101)]]
    g.d.polygon([(ox, oy)] + pts + [(ox + s, oy)], fill=(224, 238, 255))
    g.d.line([(ox, oy), (ox + s, oy - s)], fill=MUTED, width=3)
    g.d.line(pts, fill=EDGES[1], width=5, joint="curve")
    g.label((ox + s / 2, oy + 50), "False positive rate", size=26, anchor="mm")
    g.label((ox - 30, oy - s / 2), "True positive\nrate (recall)", size=24, anchor="rm")
    g.label((ox + s * 0.68, oy - s * 0.5), "Random guess\n(AUC = 0.5)", size=22, anchor="lt")
    g.label((ox + s * 0.18, oy - s * 0.86), "Good model", size=24, color=EDGES[1], anchor="lb")
    g.label((ox + s * 0.62, oy - s * 0.18), "AUC: area under\nthe curve", size=22, anchor="mm")
    g.save()
    return g


def splits():
    g = Diagram(
        "data-splits",
        "How a dataset is usually split. Name the hidden part.",
        "Train on the training set, tune hyperparameters and choose models on the validation set, and touch the test "
        "set once at the end. Cross-validation rotates the validation fold when data is scarce.",
        h=620,
    )
    g.title("Splitting the data")
    y0, y1 = 200, 330
    x = 60
    parts = [("Training set\n~70%", 0.70, 1), ("Validation\n~15%", 0.15, 4), ("Test\n~15%", 0.15, 0)]
    for text, frac, color in parts:
        w = (W - 120) * frac
        g.box((x, y0, x + w - 6, y1), text, color, size=26 if frac > 0.2 else 22, radius=12)
        x += w
    g.label((60 + (W - 120) * 0.35, 400), "Fit the weights", size=24, color=MUTED, anchor="mt", mask=False)
    g.label((60 + (W - 120) * 0.775, 400), "Tune", size=24, color=MUTED, anchor="mt", mask=False)
    g.label((60 + (W - 120) * 0.925, 400), "Final\nscore", size=24, color=MUTED, anchor="mt", mask=False)
    g.label((W / 2, 540), "Split first, then fit scalers and encoders on the training set only", size=24, anchor="mm")
    g.save()
    return g


def main():
    diagrams = [
        transformer(),
        confusion(),
        network(),
        fitting(),
        roc(),
        splits(),
        flow(
            "cnn-pipeline",
            "Convolutional neural network",
            "How a CNN classifies an image, in order. Name the hidden step.",
            "Convolutions find local patterns (edges, then shapes, then parts); pooling shrinks the map; the last "
            "layers turn features into class scores.",
            ["Input image", "Convolution\n(filters)", "ReLU", "Pooling", "Flatten", "Fully connected", "Softmax:\nclass probabilities"],
            cols=2,
        ),
        flow(
            "rag-pipeline",
            "Retrieval-augmented generation (RAG)",
            "How a RAG system answers a question, in order. Name the hidden step.",
            "Documents are split into chunks and embedded ahead of time. At question time the closest chunks are "
            "retrieved and put in the prompt, so the model answers from your sources.",
            ["User question", "Embed the question", "Search the vector\ndatabase", "Top-k chunks\n(+ reranking)", "Prompt = question\n+ chunks", "LLM", "Answer with sources"],
            cols=2,
        ),
        flow(
            "ml-lifecycle",
            "The ML project lifecycle",
            "The life of an ML project, in order. Name the hidden stage.",
            "Most of the time goes into data. Monitoring closes the loop: drift in production sends you back to "
            "collect new data and retrain.",
            ["Define the problem\n& metric", "Collect data", "Clean & label", "Features", "Train", "Evaluate", "Deploy", "Monitor"],
            cols=2,
            loop=True,
        ),
        flow(
            "rlhf-pipeline",
            "How a chat model is trained",
            "From raw text to a chat assistant, in order. Name the hidden stage.",
            "Pre-training teaches language and facts; supervised fine-tuning teaches the assistant format; preference "
            "tuning (RLHF with PPO, or DPO) teaches which answers people prefer.",
            ["Pre-training on\nweb-scale text", "Supervised fine-tuning\n(example dialogues)", "Collect human\npreference rankings", "Train a reward model", "RL fine-tuning (PPO)\nagainst the reward", "Chat assistant"],
            cols=2,
        ),
        flow(
            "agent-loop",
            "An LLM agent",
            "How an LLM agent works, step by step. Name the hidden step.",
            "The loop repeats until the model decides the goal is met. Tools can be search, code execution, APIs or "
            "files; each result is fed back as an observation.",
            ["Goal from the user", "LLM plans the\nnext step", "Calls a tool", "Observation\n(tool result)", "Enough to\nfinish?", "Final answer"],
            cols=2,
        ),
    ]
    rows = []
    for g in diagrams:
        occ = {"mode": "one", "masks": [{k: m[k] for k in ("x", "y", "w", "h", "group")} for m in g.masks]}
        answers = "; ".join(m["answer"] for m in g.masks)
        rows.append((g.name, g.header, g.extra, json.dumps(occ, separators=(",", ":")), answers))
    with open(OUT_KT, "w") as f:
        f.write("package app.mnemo.data.seed\n\n")
        f.write("// Generated by scripts/seed/ai_diagrams.py: do not edit by hand.\n\n")
        f.write("/** The diagrams of the AI & ML deck: image, question, explanation, occlusion boxes, labels. */\n")
        f.write("internal object AiDiagrams {\n")
        f.write("    class Diagram(\n        val image: String,\n        val header: String,\n        val extra: String,\n"
                "        val occlusions: String,\n        val labels: String,\n    )\n\n")
        f.write("    val all =\n        listOf(\n")
        for name, header, extra, occ, answers in rows:
            def q(s):
                return json.dumps(s, ensure_ascii=False).replace("$", "\\$")
            f.write("            Diagram(\n")
            f.write(f"                {q(name + '.webp')},\n")
            f.write(f"                {q(header)},\n")
            f.write(f"                {q(extra)},\n")
            f.write(f"                {q(occ)},\n")
            f.write(f"                {q(answers)},\n")
            f.write("            ),\n")
        f.write("        )\n}\n")
    print("wrote", len(rows), "diagrams,", sum(len(json.loads(r[3])["masks"]) for r in rows), "cards")


if __name__ == "__main__":
    main()
