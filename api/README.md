# API библиотеки

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Страница библиотекаря: http://127.0.0.1:8000/librarian  
Вход: `librarian@library.local` / `librarian`

Эмулятор Android ходит на сервер по адресу `http://10.0.2.2:8000/`.
