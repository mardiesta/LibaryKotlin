import os
import tempfile

_fd, _path = tempfile.mkstemp(prefix="library-", suffix=".db")
os.close(_fd)
os.environ["DATABASE_URL"] = f"sqlite:///{_path}"
os.environ["JWT_SECRET"] = "test-secret-key-at-least-32-bytes"

import pytest
from fastapi.testclient import TestClient

from app.database import Base, engine
from app.main import app


@pytest.fixture
def client():
    Base.metadata.drop_all(bind=engine)
    with TestClient(app) as test_client:
        yield test_client
