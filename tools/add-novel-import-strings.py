"""为本地小说导入功能补齐 13 个语言目录的字符串。

Android 字符串资源里的撇号必须转义，否则 aapt 会报错，这里统一处理。
脚本幂等：目标文件已存在同名 key 时跳过，便于重复执行。
"""
from pathlib import Path
import sys

REPO_ROOT = Path(__file__).resolve().parent.parent
RES = REPO_ROOT / "app" / "src" / "main" / "res"

TEXTS = {
    "values": {
        "story_import_title": "导入本地小说",
        "story_import_desc": "支持整本自动分章，也可多选散装章节文件",
        "story_import_pick": "选择文件",
        "story_import_reading": "正在解析…",
        "novel_import_summary": "%1$d 个文件 · %2$d 章 · 约 %3$d 字",
        "story_import_replace": "重新选择",
        "story_import_clear": "放弃导入",
        "story_import_more_chapters": "还有 %1$d 章未展开",
        "story_import_mode_hint": "已进入导入模式，创建时会写入全部章节",
        "story_import_failed": "读取小说失败",
        "story_import_empty": "没有解析出可用内容",
    },
    "values-b+zh+Hans": {
        "story_import_title": "导入本地小说",
        "story_import_desc": "支持整本自动分章，也可多选散装章节文件",
        "story_import_pick": "选择文件",
        "story_import_reading": "正在解析…",
        "novel_import_summary": "%1$d 个文件 · %2$d 章 · 约 %3$d 字",
        "story_import_replace": "重新选择",
        "story_import_clear": "放弃导入",
        "story_import_more_chapters": "还有 %1$d 章未展开",
        "story_import_mode_hint": "已进入导入模式，创建时会写入全部章节",
        "story_import_failed": "读取小说失败",
        "story_import_empty": "没有解析出可用内容",
    },
    "values-b+zh+Hant": {
        "story_import_title": "匯入本機小說",
        "story_import_desc": "支援整本自動分章，也可多選散裝章節檔案",
        "story_import_pick": "選擇檔案",
        "story_import_reading": "正在解析…",
        "novel_import_summary": "%1$d 個檔案 · %2$d 章 · 約 %3$d 字",
        "story_import_replace": "重新選擇",
        "story_import_clear": "放棄匯入",
        "story_import_more_chapters": "還有 %1$d 章未展開",
        "story_import_mode_hint": "已進入匯入模式，建立時會寫入全部章節",
        "story_import_failed": "讀取小說失敗",
        "story_import_empty": "沒有解析出可用內容",
    },
    "values-b+pt+BR": {
        "story_import_title": "Importar romance local",
        "story_import_desc": "Divide um livro inteiro ou permite escolher vários arquivos de capítulos",
        "story_import_pick": "Escolher arquivo",
        "story_import_reading": "Analisando…",
        "novel_import_summary": "%1$d arquivos · %2$d capítulos · ~%3$d caracteres",
        "story_import_replace": "Escolher outro",
        "story_import_clear": "Descartar importação",
        "story_import_more_chapters": "Mais %1$d capítulos",
        "story_import_mode_hint": "Modo de importação ativo; todos os capítulos serão gravados ao criar",
        "story_import_failed": "Não foi possível ler o romance",
        "story_import_empty": "Nenhum conteúdo utilizável",
    },
    "values-de": {
        "story_import_title": "Lokalen Roman importieren",
        "story_import_desc": "Ganzes Buch automatisch aufteilen oder mehrere Kapiteldateien auswählen",
        "story_import_pick": "Datei wählen",
        "story_import_reading": "Wird analysiert…",
        "novel_import_summary": "%1$d Dateien · %2$d Kapitel · ca. %3$d Zeichen",
        "story_import_replace": "Neu wählen",
        "story_import_clear": "Import verwerfen",
        "story_import_more_chapters": "%1$d weitere Kapitel",
        "story_import_mode_hint": "Importmodus aktiv; beim Erstellen werden alle Kapitel geschrieben",
        "story_import_failed": "Roman konnte nicht gelesen werden",
        "story_import_empty": "Kein verwertbarer Inhalt",
    },
    "values-es": {
        "story_import_title": "Importar novela local",
        "story_import_desc": "Divide un libro completo o permite elegir varios archivos de capítulos",
        "story_import_pick": "Elegir archivo",
        "story_import_reading": "Analizando…",
        "novel_import_summary": "%1$d archivos · %2$d capítulos · ~%3$d caracteres",
        "story_import_replace": "Elegir otro",
        "story_import_clear": "Descartar importación",
        "story_import_more_chapters": "%1$d capítulos más",
        "story_import_mode_hint": "Modo importación activo; se escribirán todos los capítulos al crear",
        "story_import_failed": "No se pudo leer la novela",
        "story_import_empty": "Sin contenido utilizable",
    },
    "values-fr": {
        "story_import_title": "Importer un roman local",
        "story_import_desc": "Découpe automatique d\\'un livre entier ou sélection de plusieurs fichiers de chapitres",
        "story_import_pick": "Choisir un fichier",
        "story_import_reading": "Analyse…",
        "novel_import_summary": "%1$d fichiers · %2$d chapitres · ~%3$d caractères",
        "story_import_replace": "Choisir à nouveau",
        "story_import_clear": "Abandonner l\\'import",
        "story_import_more_chapters": "%1$d chapitres restants",
        "story_import_mode_hint": "Mode import actif ; tous les chapitres seront écrits à la création",
        "story_import_failed": "Impossible de lire le roman",
        "story_import_empty": "Aucun contenu exploitable",
    },
    "values-id": {
        "story_import_title": "Impor novel lokal",
        "story_import_desc": "Pisahkan buku utuh otomatis atau pilih beberapa berkas bab",
        "story_import_pick": "Pilih berkas",
        "story_import_reading": "Menganalisis…",
        "novel_import_summary": "%1$d berkas · %2$d bab · ~%3$d karakter",
        "story_import_replace": "Pilih ulang",
        "story_import_clear": "Batalkan impor",
        "story_import_more_chapters": "%1$d bab lagi",
        "story_import_mode_hint": "Mode impor aktif; semua bab ditulis saat dibuat",
        "story_import_failed": "Gagal membaca novel",
        "story_import_empty": "Tidak ada konten yang bisa dipakai",
    },
    "values-it": {
        "story_import_title": "Importa romanzo locale",
        "story_import_desc": "Divide un libro intero o consente di scegliere più file di capitoli",
        "story_import_pick": "Scegli file",
        "story_import_reading": "Analisi…",
        "novel_import_summary": "%1$d file · %2$d capitoli · ~%3$d caratteri",
        "story_import_replace": "Scegli di nuovo",
        "story_import_clear": "Annulla importazione",
        "story_import_more_chapters": "Altri %1$d capitoli",
        "story_import_mode_hint": "Modalità importazione attiva; tutti i capitoli saranno scritti alla creazione",
        "story_import_failed": "Impossibile leggere il romanzo",
        "story_import_empty": "Nessun contenuto utilizzabile",
    },
    "values-ja": {
        "story_import_title": "ローカル小説を読み込む",
        "story_import_desc": "一冊まるごとの自動分割にも、分割済みファイルの複数選択にも対応",
        "story_import_pick": "ファイルを選択",
        "story_import_reading": "解析中…",
        "novel_import_summary": "%1$d ファイル · %2$d 章 · 約 %3$d 文字",
        "story_import_replace": "選び直す",
        "story_import_clear": "読み込みをやめる",
        "story_import_more_chapters": "残り %1$d 章",
        "story_import_mode_hint": "読み込みモードです。作成時に全章を書き込みます",
        "story_import_failed": "小説の読み込みに失敗しました",
        "story_import_empty": "解析できる内容がありません",
    },
    "values-ko": {
        "story_import_title": "로컬 소설 가져오기",
        "story_import_desc": "한 권 전체 자동 분할과 낱개 장 파일 다중 선택을 지원합니다",
        "story_import_pick": "파일 선택",
        "story_import_reading": "분석 중…",
        "novel_import_summary": "%1$d개 파일 · %2$d장 · 약 %3$d자",
        "story_import_replace": "다시 선택",
        "story_import_clear": "가져오기 취소",
        "story_import_more_chapters": "남은 %1$d장",
        "story_import_mode_hint": "가져오기 모드입니다. 생성 시 모든 장을 기록합니다",
        "story_import_failed": "소설을 읽지 못했습니다",
        "story_import_empty": "분석된 내용이 없습니다",
    },
    "values-ru": {
        "story_import_title": "Импорт локального романа",
        "story_import_desc": "Автоматическое разделение книги или выбор нескольких файлов глав",
        "story_import_pick": "Выбрать файл",
        "story_import_reading": "Анализ…",
        "novel_import_summary": "Файлов: %1$d · глав: %2$d · ~%3$d символов",
        "story_import_replace": "Выбрать снова",
        "story_import_clear": "Отменить импорт",
        "story_import_more_chapters": "Ещё %1$d глав",
        "story_import_mode_hint": "Режим импорта: при создании будут записаны все главы",
        "story_import_failed": "Не удалось прочитать роман",
        "story_import_empty": "Нет пригодного содержимого",
    },
    "values-vi": {
        "story_import_title": "Nhập tiểu thuyết cục bộ",
        "story_import_desc": "Tự chia cả cuốn hoặc chọn nhiều tệp chương rời",
        "story_import_pick": "Chọn tệp",
        "story_import_reading": "Đang phân tích…",
        "novel_import_summary": "%1$d tệp · %2$d chương · ~%3$d ký tự",
        "story_import_replace": "Chọn lại",
        "story_import_clear": "Bỏ nhập",
        "story_import_more_chapters": "Còn %1$d chương",
        "story_import_mode_hint": "Đang ở chế độ nhập; sẽ ghi toàn bộ chương khi tạo",
        "story_import_failed": "Không đọc được tiểu thuyết",
        "story_import_empty": "Không có nội dung dùng được",
    },
}


def main() -> int:
    missing = []
    for folder, entries in TEXTS.items():
        path = RES / folder / "strings.xml"
        if not path.is_file():
            missing.append(folder)
            continue
        text = path.read_text(encoding="utf-8")
        # 只补充尚不存在的 key，保证脚本可重复执行
        additions = [
            f'    <string name="{key}">{value}</string>'
            for key, value in entries.items()
            if f'name="{key}"' not in text
        ]
        if not additions:
            continue
        if "</resources>" not in text:
            raise SystemExit(f"缺少 resources 结束标签: {path}")
        text = text.replace("</resources>", "\n".join(additions) + "\n</resources>")
        path.write_text(text, encoding="utf-8", newline="\n")
        print(f"{folder}: +{len(additions)}")
    if missing:
        print("缺失目录:", ", ".join(missing))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
