/*
 * ノート編集画面のWYSIWYGエディタ(contenteditable)側のスクリプト。
 * Java(RichNoteEditor)が WebEngine.executeScript で window.MDE の関数を呼び、書式の適用・ブロックの変換・
 * 配置・HTMLの取得を行う。HTML→Markdownの変換はJava側(HtmlToMarkdown)で行うので、ここではDOMを
 * 「レンダラーが出す形」に近い素直な構造（p/h1-6/ul・ol>li/blockquote/pre/table/hr、段落内の改行はbr）に保つ。
 */
(function () {
  'use strict';

  var ED = document.getElementById('ed');
  var dirty = false;
  var saved = null;

  var BLOCKS = {P: 1, DIV: 1, H1: 1, H2: 1, H3: 1, H4: 1, H5: 1, H6: 1, UL: 1, OL: 1, LI: 1,
    BLOCKQUOTE: 1, PRE: 1, TABLE: 1, HR: 1};
  var MATH_OPTIONS = {
    delimiters: [{left: '$$', right: '$$', display: true}, {left: '$', right: '$', display: false}],
    throwOnError: false
  };

  function each(list, fn) {
    Array.prototype.forEach.call(list, fn);
  }

  function isEl(n) {
    return !!n && n.nodeType === 1;
  }

  function isBlockEl(n) {
    return isEl(n) && !!BLOCKS[n.tagName];
  }

  function markDirty() {
    dirty = true;
  }

  function indexOf(node) {
    return Array.prototype.indexOf.call(node.parentNode.childNodes, node);
  }

  function nodeLength(node) {
    return node.nodeType === 3 ? node.nodeValue.length : node.childNodes.length;
  }

  function newEl(tag) {
    return document.createElement(tag);
  }

  function emptyBlock(tag) {
    var el = newEl(tag || 'p');
    el.appendChild(newEl('br'));
    return el;
  }

  // ---------------------------------------------------------------- 選択範囲

  function currentRange() {
    var s = window.getSelection();
    if (s && s.rangeCount) {
      var r = s.getRangeAt(0);
      if (ED.contains(r.startContainer) && ED.contains(r.endContainer)) {
        return r;
      }
    }
    return null;
  }

  document.addEventListener('selectionchange', function () {
    var r = currentRange();
    if (r) {
      saved = r.cloneRange();
    }
  });

  function setRange(r) {
    var s = window.getSelection();
    s.removeAllRanges();
    s.addRange(r);
  }

  /** ツールバーなど別の部品に操作が移っても、直前の選択範囲を取り戻して編集欄にフォーカスする。 */
  function activate() {
    var want = currentRange() ? currentRange().cloneRange() : (saved ? saved.cloneRange() : null);
    ED.focus();
    if (want) {
      setRange(want);
    } else if (!currentRange()) {
      var r = document.createRange();
      r.selectNodeContents(ED);
      r.collapse(false);
      setRange(r);
    }
    return currentRange();
  }

  function pointOk(node, offset) {
    return !!node && document.body.contains(node) && offset <= nodeLength(node);
  }

  /**
   * 選択の境界点を、DOMを組み替えた後でも辿れる形で控える。境界が要素(段落など)＋位置のときは、
   * その位置の前後の子ノードを目印にする（要素そのものが作り替えで取り除かれても、子は移って残るため）。
   */
  function capturePoint(node, offset) {
    var p = {node: node, offset: offset, next: null, prev: null};
    if (isEl(node)) {
      p.next = node.childNodes[offset] || null;
      p.prev = offset > 0 ? node.childNodes[offset - 1] : null;
    }
    return p;
  }

  function resolvePoint(p) {
    if (isEl(p.node)) {
      if (p.next && document.body.contains(p.next)) {
        return [p.next.parentNode, indexOf(p.next)];
      }
      if (p.prev && document.body.contains(p.prev)) {
        return [p.prev.parentNode, indexOf(p.prev) + 1];
      }
    }
    return pointOk(p.node, p.offset) ? [p.node, p.offset] : null;
  }

  function captureRange(r) {
    return {start: capturePoint(r.startContainer, r.startOffset), end: capturePoint(r.endContainer, r.endOffset)};
  }

  /** 控えた範囲を取り戻す。戻せたらtrue。 */
  function restoreRange(c) {
    var s = resolvePoint(c.start);
    var e = resolvePoint(c.end);
    if (!s || !e) {
      return false;
    }
    var nr = document.createRange();
    nr.setStart(s[0], s[1]);
    nr.setEnd(e[0], e[1]);
    setRange(nr);
    return true;
  }

  /** DOMを組み替える処理の前後で選択範囲を保つ。戻せなかったときはfallback()で選び直す。 */
  function keepSelection(fn, fallback) {
    var captured = captureRange(activate());
    fn();
    if (!restoreRange(captured) && fallback) {
      fallback();
    }
  }

  function selectNodeContents(el) {
    var r = document.createRange();
    r.selectNodeContents(el);
    setRange(r);
  }

  function caretAtEnd(el) {
    var r = document.createRange();
    r.selectNodeContents(el);
    r.collapse(false);
    setRange(r);
  }

  function caretAtStart(el) {
    var r = document.createRange();
    r.selectNodeContents(el);
    r.collapse(true);
    setRange(r);
  }

  function selectAcross(first, last, collapsed) {
    var r = document.createRange();
    r.selectNodeContents(last);
    r.collapse(false);
    if (!collapsed) {
      r.setStart(first, 0);
    }
    setRange(r);
  }

  // ---------------------------------------------------------------- ブロックと行

  /** エディタ直下に裸で置かれた文字・インライン要素を段落(p)で包む。 */
  function ensureBlocks() {
    // 文字のノードを段落へ移すと、選択範囲が親(エディタ直下)へ寄せられてしまうので、前後で保つ。
    var before = currentRange();
    var captured = before ? captureRange(before) : null;
    wrapBareNodes();
    if (captured) {
      restoreRange(captured);
    }
  }

  function wrapBareNodes() {
    var kids = Array.prototype.slice.call(ED.childNodes);
    var run = [];

    function flush() {
      if (!run.length) {
        return;
      }
      var meaningful = run.some(function (n) {
        return n.nodeType !== 3 || /\S/.test(n.nodeValue);
      });
      if (meaningful) {
        var p = newEl('p');
        ED.insertBefore(p, run[0]);
        run.forEach(function (n) {
          p.appendChild(n);
        });
      } else {
        run.forEach(function (n) {
          ED.removeChild(n);
        });
      }
      run = [];
    }

    kids.forEach(function (n) {
      if (isBlockEl(n)) {
        flush();
      } else {
        run.push(n);
      }
    });
    flush();
    if (!ED.firstChild) {
      ED.appendChild(emptyBlock());
    }
  }

  function blockOf(node) {
    var n = node;
    while (n && n !== ED) {
      if (isBlockEl(n)) {
        // 引用の中の段落は、引用ブロック全体を1つのブロックとして扱う。
        if ((n.tagName === 'P' || n.tagName === 'DIV') && n.parentNode && n.parentNode.tagName === 'BLOCKQUOTE') {
          return n.parentNode;
        }
        return n;
      }
      n = n.parentNode;
    }
    return null;
  }

  function hasBlockChild(el) {
    for (var i = 0; i < el.children.length; i++) {
      if (isBlockEl(el.children[i])) {
        return true;
      }
    }
    return false;
  }

  function isPlain(el) {
    return (el.tagName === 'P' || el.tagName === 'DIV') && !el.classList.contains('checkbox-item');
  }

  /** br区切りの「行」ごとのノード配列。 */
  function linesOf(block) {
    var lines = [[]];
    each(block.childNodes, function (n) {
      if (n.nodeName === 'BR') {
        lines.push([]);
      } else {
        lines[lines.length - 1].push(n);
      }
    });
    return lines;
  }

  function lineIndexAt(block, node, offset) {
    var idx;
    if (node === block) {
      idx = offset;
    } else {
      var c = node;
      while (c && c.parentNode !== block) {
        c = c.parentNode;
      }
      if (!c) {
        return 0;
      }
      idx = indexOf(c);
    }
    var n = 0;
    for (var i = 0; i < idx; i++) {
      if (block.childNodes[i].nodeName === 'BR') {
        n++;
      }
    }
    return n;
  }

  /** 範囲の終点が、行の先頭ちょうど（直前がbr）にあるか。 */
  function endsAtLineStart(block, node, offset) {
    var prev = null;
    if (node === block) {
      prev = offset > 0 ? block.childNodes[offset - 1] : null;
    } else if (offset === 0) {
      var c = node;
      while (c && c.parentNode !== block) {
        c = c.parentNode;
      }
      prev = c ? c.previousSibling : null;
    }
    return !!prev && prev.nodeName === 'BR';
  }

  function leafBlocksIn(range) {
    var res = [];
    each(ED.querySelectorAll('p,div,h1,h2,h3,h4,h5,h6,li,blockquote,pre,table,hr'), function (el) {
      // 引用は中に段落(div/p)を持ちうるが、全体を1つのブロックとして扱う。
      if (hasBlockChild(el) && el.tagName !== 'TABLE' && el.tagName !== 'BLOCKQUOTE') {
        return;
      }
      if (el.parentNode && el.parentNode.tagName === 'BLOCKQUOTE') {
        return;
      }
      if (el.tagName !== 'TABLE' && el.closest('table')) {
        return;
      }
      var r2 = document.createRange();
      r2.selectNodeContents(el);
      if (range.compareBoundaryPoints(Range.START_TO_END, r2) > 0
        && range.compareBoundaryPoints(Range.END_TO_START, r2) < 0) {
        res.push(el);
      }
    });
    return res;
  }

  /** 選択範囲にかかるブロックと、そのうち選択された行の範囲[from,to]。 */
  function getTargets() {
    ensureBlocks();
    var r = activate();
    var blocks;
    if (r.collapsed) {
      var b = blockOf(r.startContainer);
      blocks = b ? [b] : [];
    } else {
      blocks = leafBlocksIn(r);
    }
    var targets = [];
    blocks.forEach(function (b) {
      var t = {block: b, from: 0, to: 0};
      if (isPlain(b)) {
        var total = linesOf(b).length;
        t.to = total - 1;
        if (b.contains(r.startContainer)) {
          t.from = lineIndexAt(b, r.startContainer, r.startOffset);
        }
        if (r.collapsed) {
          t.to = t.from;
        } else if (b.contains(r.endContainer)) {
          t.to = lineIndexAt(b, r.endContainer, r.endOffset);
          if (t.to > t.from && endsAtLineStart(b, r.endContainer, r.endOffset)) {
            t.to--;
          }
        }
        if (t.to < t.from) {
          t.to = t.from;
        }
      }
      targets.push(t);
    });
    return targets;
  }

  function makeBlock(proto, lines) {
    var el = proto.cloneNode(false);
    lines.forEach(function (nodes, i) {
      if (i > 0) {
        el.appendChild(newEl('br'));
      }
      nodes.forEach(function (n) {
        el.appendChild(n);
      });
    });
    if (!el.firstChild) {
      el.appendChild(newEl('br'));
    }
    return el;
  }

  /**
   * 段落(p/div)を、行[from,to]を含むブロックとその前後のブロックに分ける。選択された側のブロックを返す
   * （perLineなら選択行を1行ずつ別ブロックにする）。分ける必要が無ければ元のブロックをそのまま返す。
   */
  function splitBlock(block, from, to, perLine) {
    var lines = linesOf(block);
    var groups = [];
    if (from > 0) {
      groups.push({lines: lines.slice(0, from), sel: false});
    }
    if (perLine) {
      for (var i = from; i <= to; i++) {
        groups.push({lines: [lines[i]], sel: true});
      }
    } else {
      groups.push({lines: lines.slice(from, to + 1), sel: true});
    }
    if (to < lines.length - 1) {
      groups.push({lines: lines.slice(to + 1), sel: false});
    }
    if (groups.length === 1) {
      return [block];
    }
    var parent = block.parentNode;
    var out = [];
    groups.forEach(function (g) {
      var el = makeBlock(block, g.lines);
      parent.insertBefore(el, block);
      if (g.sel) {
        out.push(el);
      }
    });
    parent.removeChild(block);
    return out;
  }

  // ---------------------------------------------------------------- 配置

  function cleanStyle(el) {
    if (!el.getAttribute('style')) {
      el.removeAttribute('style');
    }
  }

  function applyAlign(el, a) {
    if (el.tagName === 'TABLE') {
      if (a === 'left') {
        el.removeAttribute('data-align');
        el.style.marginLeft = '';
        el.style.marginRight = '';
      } else {
        el.setAttribute('data-align', a);
        el.style.marginLeft = 'auto';
        el.style.marginRight = a === 'center' ? 'auto' : '0';
      }
      cleanStyle(el);
      return;
    }
    if (el.tagName === 'HR' || el.tagName === 'PRE') {
      return;
    }
    el.style.textAlign = a === 'left' ? '' : a;
    cleanStyle(el);
    // 画像はdisplay:blockなので親のtext-alignでは動かない。表示されている画像自体を寄せる。
    each(el.querySelectorAll('img'), function (img) {
      if (a === 'left') {
        img.style.display = '';
        img.style.margin = '';
      } else {
        img.style.display = 'block';
        img.style.margin = a === 'center' ? '0 auto' : '0 0 0 auto';
      }
      cleanStyle(img);
    });
  }

  function setAlign(a) {
    var targets = getTargets();
    var done = [];
    keepSelection(function () {
      targets.forEach(function (t) {
        var els = isPlain(t.block) ? splitBlock(t.block, t.from, t.to, false) : [t.block];
        els.forEach(function (el) {
          applyAlign(el, a);
          done.push(el);
        });
      });
    }, function () {
      if (done.length) {
        selectAcross(done[0], done[done.length - 1], false);
      }
    });
    markDirty();
  }

  // ---------------------------------------------------------------- ブロックの種類の変更

  function matchesType(el, spec) {
    switch (spec.type) {
      case 'p':
        return isPlain(el);
      case 'h':
        return el.tagName === 'H' + spec.level;
      case 'quote':
        return el.tagName === 'BLOCKQUOTE';
      case 'pre':
        return el.tagName === 'PRE';
      case 'ul':
        return el.tagName === 'LI' && el.parentNode && el.parentNode.tagName === 'UL';
      case 'ol':
        return el.tagName === 'LI' && el.parentNode && el.parentNode.tagName === 'OL';
    }
    return false;
  }

  function detachFromList(li) {
    var list = li.parentNode;
    if (!list || (list.tagName !== 'UL' && list.tagName !== 'OL')) {
      return;
    }
    var after = null;
    if (li.nextSibling) {
      after = list.cloneNode(false);
      while (li.nextSibling) {
        after.appendChild(li.nextSibling);
      }
    }
    list.parentNode.insertBefore(li, list.nextSibling);
    if (after) {
      list.parentNode.insertBefore(after, li.nextSibling);
    }
    if (!list.firstChild) {
      list.parentNode.removeChild(list);
    }
  }

  function appendTextLines(to, text) {
    text.replace(/\r/g, '').split('\n').forEach(function (line, i) {
      if (i > 0) {
        to.appendChild(newEl('br'));
      }
      if (line) {
        to.appendChild(document.createTextNode(line));
      }
    });
  }

  function plainTextOf(el) {
    var out = '';
    (function walk(n) {
      each(n.childNodes, function (c) {
        if (c.nodeType === 3) {
          out += c.nodeValue;
        } else if (c.nodeName === 'BR') {
          out += '\n';
        } else if (isEl(c)) {
          if (isBlockEl(c) && out && out.charAt(out.length - 1) !== '\n') {
            out += '\n';
          }
          walk(c);
        }
      });
    })(el);
    return out.replace(/\n+$/, '');
  }

  function moveInline(from, to) {
    if (from.tagName === 'PRE') {
      appendTextLines(to, plainTextOf(from));
    } else {
      each(Array.prototype.slice.call(from.childNodes), function (c) {
        if (isBlockEl(c) && c.tagName !== 'UL' && c.tagName !== 'OL') {
          if (to.lastChild && to.lastChild.nodeName !== 'BR') {
            to.appendChild(newEl('br'));
          }
          moveInline(c, to);
        } else if (!(isEl(c) && (c.tagName === 'UL' || c.tagName === 'OL'))) {
          to.appendChild(c);
        }
      });
    }
    // 末尾のbrは、空行にキャレットを置くための目印として1つだけ残す。
    if (!to.firstChild) {
      to.appendChild(newEl('br'));
    }
  }

  /** ブロック1つを指定の種類に作り替え、作ったブロック（リストなら項目li）を返す。 */
  function convertBlock(el, target) {
    if (el.tagName === 'TABLE' || el.tagName === 'HR') {
      return el;
    }
    var align = el.style ? el.style.textAlign : '';
    if (el.tagName === 'LI') {
      detachFromList(el);
    }
    var made;
    var returned;
    switch (target.type) {
      case 'h':
        made = newEl('h' + target.level);
        returned = made;
        moveInline(el, made);
        break;
      case 'quote':
        made = newEl('blockquote');
        returned = made;
        moveInline(el, made);
        break;
      case 'ul':
      case 'ol':
        returned = newEl('li');
        moveInline(el, returned);
        made = newEl(target.type);
        made.appendChild(returned);
        break;
      case 'pre':
        made = newEl('pre');
        var code = newEl('code');
        appendTextLines(code, plainTextOf(el));
        if (!code.firstChild) {
          code.appendChild(newEl('br'));
        }
        made.appendChild(code);
        returned = made;
        align = '';
        break;
      default:
        made = newEl('p');
        returned = made;
        moveInline(el, made);
    }
    if (align) {
      returned.style.textAlign = align;
    }
    el.parentNode.replaceChild(made, el);
    return returned;
  }

  function mergeAdjacentLists() {
    var n = ED.firstChild;
    while (n) {
      var next = n.nextSibling;
      if (isEl(n) && (n.tagName === 'UL' || n.tagName === 'OL') && isEl(n.previousSibling)
        && n.previousSibling.tagName === n.tagName) {
        while (n.firstChild) {
          n.previousSibling.appendChild(n.firstChild);
        }
        ED.removeChild(n);
      }
      n = next;
    }
  }

  function setBlockType(spec) {
    var targets = getTargets();
    var before = currentRange();
    var captured = before ? captureRange(before) : null;
    var wasCollapsed = !before || before.collapsed;
    var els = [];
    targets.forEach(function (t) {
      if (isPlain(t.block)) {
        els = els.concat(splitBlock(t.block, t.from, t.to, spec.type !== 'pre'));
      } else {
        els.push(t.block);
      }
    });
    var convertible = els.filter(function (el) {
      return el.tagName !== 'TABLE' && el.tagName !== 'HR';
    });
    if (!convertible.length) {
      return;
    }
    var allSame = !spec.force && convertible.every(function (el) {
      return matchesType(el, spec);
    });
    var target = allSame || spec.type === 'p' ? {type: 'p'} : spec;
    var created = convertible.map(function (el) {
      return convertBlock(el, target);
    });
    mergeAdjacentLists();
    // 文字のノードは作り替え後のブロックへそのまま移っているので、カーソル位置を元の場所へ戻せる。
    if (!captured || !restoreRange(captured)) {
      selectAcross(created[0], created[created.length - 1], wasCollapsed);
    }
    markDirty();
  }

  // ---------------------------------------------------------------- 挿入

  function topLevel(el) {
    var top = el;
    while (top && top.parentNode !== ED) {
      top = top.parentNode;
    }
    return top;
  }

  function insertBlockAfterCaret(el) {
    ensureBlocks();
    var r = activate();
    var b = blockOf(r.endContainer) || ED.lastChild;
    var top = topLevel(b) || ED.lastChild;
    ED.insertBefore(el, top ? top.nextSibling : null);
    var next = el.nextSibling;
    if (!next || !isBlockEl(next) || next.tagName === 'HR' || next.tagName === 'TABLE') {
      next = emptyBlock();
      ED.insertBefore(next, el.nextSibling);
    }
    return next;
  }

  function insertHr() {
    var next = insertBlockAfterCaret(newEl('hr'));
    caretAtStart(next);
    markDirty();
  }

  function insertTable(rows, cols) {
    var table = newEl('table');
    for (var r = 0; r < rows; r++) {
      var tr = newEl('tr');
      for (var c = 0; c < cols; c++) {
        var cell = newEl(r === 0 ? 'th' : 'td');
        cell.appendChild(newEl('br'));
        tr.appendChild(cell);
      }
      table.appendChild(tr);
    }
    insertBlockAfterCaret(table);
    caretAtStart(table.querySelector('th,td'));
    markDirty();
  }

  // ---------------------------------------------------------------- インライン書式

  function cmd(name) {
    activate();
    document.execCommand(name, false, null);
    markDirty();
  }

  function replaceSizeFonts(value) {
    each(ED.querySelectorAll('font[size="7"]'), function (f) {
      var span = newEl('span');
      span.style.fontSize = value;
      while (f.firstChild) {
        span.appendChild(f.firstChild);
      }
      f.parentNode.replaceChild(span, f);
      // 入れ子になった内側のサイズ指定は、外側で指定し直したので不要。
      each(span.querySelectorAll('span'), function (inner) {
        if (inner.style.fontSize && inner !== span) {
          inner.style.fontSize = '';
          cleanStyle(inner);
        }
      });
    });
  }

  function unwrapIfBare(el) {
    if (el.tagName === 'SPAN' && !el.getAttribute('style') && !el.className) {
      while (el.firstChild) {
        el.parentNode.insertBefore(el.firstChild, el);
      }
      el.parentNode.removeChild(el);
    }
  }

  function clearStyle(prop) {
    var r = activate();
    var camel = prop.replace(/-([a-z])/g, function (m, c) {
      return c.toUpperCase();
    });
    var spans = [];
    if (r.collapsed) {
      var n = r.startContainer;
      while (n && n !== ED) {
        if (isEl(n) && (n.tagName === 'SPAN' || n.tagName === 'FONT')) {
          spans.push(n);
        }
        n = n.parentNode;
      }
    } else {
      each(ED.querySelectorAll('span[style],font'), function (el) {
        if (r.intersectsNode(el)) {
          spans.push(el);
        }
      });
    }
    spans.forEach(function (el) {
      el.style[camel] = '';
      if (el.tagName === 'FONT') {
        if (prop === 'color') {
          el.removeAttribute('color');
        } else if (prop === 'font-family') {
          el.removeAttribute('face');
        }
      }
      cleanStyle(el);
      unwrapIfBare(el);
    });
  }

  function applyStyle(prop, value) {
    activate();
    if (!value) {
      clearStyle(prop);
      markDirty();
      return;
    }
    document.execCommand('styleWithCSS', false, true);
    try {
      if (prop === 'color') {
        document.execCommand('foreColor', false, value);
      } else if (prop === 'background-color') {
        document.execCommand('hiliteColor', false, value);
      } else if (prop === 'font-family') {
        document.execCommand('fontName', false, value);
      } else if (prop === 'font-size') {
        document.execCommand('styleWithCSS', false, false);
        document.execCommand('fontSize', false, '7');
        replaceSizeFonts(value);
      }
    } finally {
      document.execCommand('styleWithCSS', false, false);
    }
    markDirty();
  }

  function selectionText() {
    var r = currentRange() || saved;
    return r ? r.toString() : '';
  }

  function escapeHtml(t) {
    return t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function applyRuby(reading) {
    var r = activate();
    var rubies = [];
    var n = r.startContainer;
    while (n && n !== ED) {
      if (isEl(n) && n.tagName === 'RUBY') {
        rubies.push(n);
      }
      n = n.parentNode;
    }
    if (!r.collapsed) {
      each(ED.querySelectorAll('ruby'), function (el) {
        if (r.intersectsNode(el) && rubies.indexOf(el) < 0) {
          rubies.push(el);
        }
      });
    }
    if (rubies.length) {
      rubies.forEach(function (ruby) {
        var rt = ruby.querySelector('rt');
        if (reading && rubies.length === 1 && rt) {
          rt.textContent = reading;
          return;
        }
        var base = '';
        each(ruby.childNodes, function (c) {
          if (c.nodeName !== 'RT' && c.nodeName !== 'RP') {
            base += c.textContent;
          }
        });
        ruby.parentNode.replaceChild(document.createTextNode(base), ruby);
      });
      markDirty();
      return;
    }
    var text = r.toString();
    if (!text || !reading) {
      return;
    }
    var ruby = newEl('ruby');
    ruby.appendChild(document.createTextNode(text));
    var rt = newEl('rt');
    rt.textContent = reading;
    ruby.appendChild(rt);
    r.deleteContents();
    r.insertNode(ruby);
    selectNodeContents(ruby);
    markDirty();
  }

  function toggleInlineCode() {
    var r = activate();
    var code = null;
    var n = r.startContainer;
    while (n && n !== ED) {
      if (isEl(n) && n.tagName === 'CODE' && !(n.parentNode && n.parentNode.tagName === 'PRE')) {
        code = n;
        break;
      }
      n = n.parentNode;
    }
    if (code) {
      var text = document.createTextNode(code.textContent);
      code.parentNode.replaceChild(text, code);
    } else {
      var t = r.toString();
      if (!t) {
        return;
      }
      var el = newEl('code');
      el.textContent = t;
      r.deleteContents();
      r.insertNode(el);
      selectNodeContents(el);
    }
    markDirty();
  }

  function insertImageHtml(html) {
    ensureBlocks();
    var r = activate();
    var holder = newEl('div');
    holder.innerHTML = html;
    var a = holder.firstChild;
    if (!r.collapsed) {
      r.deleteContents();
    }
    r.insertNode(a);
    // 行末に置いたときも、画像の後ろにカーソルを置けるよう、目印のbrを添える。
    if (!a.nextSibling) {
      a.parentNode.appendChild(newEl('br'));
    }
    var nr = document.createRange();
    nr.setStartAfter(a);
    nr.collapse(true);
    setRange(nr);
    prepare();
    markDirty();
  }

  // ---------------------------------------------------------------- 読み込み・取得

  /** 画像・ノート内リンク・注釈の参照を、中身を編集できない1つの単位にし、数式をKaTeXで描画する。 */
  function prepare() {
    each(ED.querySelectorAll('a[href^="#image:"],a.note-link'), function (a) {
      a.setAttribute('contenteditable', 'false');
    });
    each(ED.querySelectorAll('a.footnote'), function (a) {
      if (a.parentNode && a.parentNode.tagName === 'SUP') {
        a.parentNode.setAttribute('contenteditable', 'false');
      }
    });
    each(ED.querySelectorAll('.md-atom'), function (atom) {
      if (!atom.getAttribute('data-done') && window.renderMathInElement) {
        try {
          window.renderMathInElement(atom, MATH_OPTIONS);
        } catch (e) {
          // 描画に失敗しても原文(data-md)は保たれるので、そのまま表示する。
        }
      }
      atom.setAttribute('data-done', '1');
    });
  }

  function setBody(html) {
    // レンダラーはbrの直後に改行文字を置く。見た目には影響しないが、文字列として扱うとき(コードブロック化など)に
    // 余計な空行になるので取り除く。
    ED.innerHTML = html.replace(/(<br\s*\/?>)\n/g, '$1');
    if (!ED.firstChild) {
      ED.appendChild(emptyBlock());
    }
    prepare();
    dirty = false;
    saved = null;
  }

  function getHtml() {
    var clone = ED.cloneNode(true);
    each(clone.querySelectorAll('.md-atom'), function (atom) {
      atom.innerHTML = '';
    });
    return clone.innerHTML;
  }

  // ---------------------------------------------------------------- イベント

  function insertTextWithBreaks(text) {
    text.replace(/\r\n?/g, '\n').split('\n').forEach(function (line, i) {
      if (i > 0) {
        document.execCommand('insertLineBreak', false, null);
      }
      if (line) {
        document.execCommand('insertText', false, line);
      }
    });
  }

  ED.addEventListener('keydown', function (e) {
    var isEnter = e.key === 'Enter' || e.keyCode === 13;
    // 日本語入力の変換確定のエンターは、ここでは扱わない。
    if (isEnter && !e.isComposing && e.keyCode !== 229) {
      var r = currentRange();
      var b = r ? blockOf(r.startContainer) : null;
      var inCell = r && !!(isEl(r.startContainer) ? r.startContainer : r.startContainer.parentNode).closest('td,th');
      if (inCell || e.shiftKey || (b && isPlain(b)) || !b) {
        e.preventDefault();
        document.execCommand('insertLineBreak', false, null);
        markDirty();
      }
    } else if ((e.key === 'Tab' || e.keyCode === 9) && !e.isComposing) {
      var rr = currentRange();
      var start = rr ? (isEl(rr.startContainer) ? rr.startContainer : rr.startContainer.parentNode) : null;
      var cell = start ? start.closest('td,th') : null;
      if (cell) {
        e.preventDefault();
        var cells = Array.prototype.slice.call(cell.closest('table').querySelectorAll('th,td'));
        var next = cells[cells.indexOf(cell) + (e.shiftKey ? -1 : 1)];
        if (next) {
          caretAtStart(next);
        }
      }
    }
  });

  ED.addEventListener('input', function () {
    markDirty();
    if (!ED.firstChild || ED.innerHTML === '<br>') {
      ED.innerHTML = '<p><br></p>';
      caretAtStart(ED.firstChild);
    }
  });

  ED.addEventListener('paste', function (e) {
    var data = e.clipboardData;
    if (data && data.getData) {
      var text = data.getData('text/plain');
      if (text) {
        e.preventDefault();
        insertTextWithBreaks(text);
        markDirty();
      }
    }
  });

  ED.addEventListener('dragover', function (e) {
    if (e.dataTransfer && e.dataTransfer.types && Array.prototype.indexOf.call(e.dataTransfer.types, 'Files') >= 0) {
      e.preventDefault();
    }
  });

  ED.addEventListener('drop', function (e) {
    if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) {
      e.preventDefault();
    }
  });

  ED.addEventListener('click', function (e) {
    var t = e.target;
    var a = t && t.closest ? t.closest('a') : null;
    if (a) {
      e.preventDefault();
    }
    var img = t && t.tagName === 'IMG' ? t : (a ? a.querySelector('img') : null);
    if (img) {
      var r = document.createRange();
      r.selectNode(a || img);
      setRange(r);
    }
  });

  // ---------------------------------------------------------------- 公開

  window.MDE = {
    setBody: setBody,
    getHtml: getHtml,
    isDirty: function () {
      return dirty;
    },
    setAppearance: function (css) {
      document.getElementById('appearance').textContent = css;
    },
    cmd: cmd,
    style: applyStyle,
    code: toggleInlineCode,
    ruby: applyRuby,
    selectionText: selectionText,
    align: setAlign,
    block: function (type) {
      setBlockType({type: type});
    },
    heading: function (level) {
      setBlockType(level > 0 ? {type: 'h', level: level} : {type: 'p', force: true});
    },
    hr: insertHr,
    table: insertTable,
    image: insertImageHtml,
    focus: function () {
      activate();
    }
  };
})();
