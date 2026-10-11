import pytest

from app import create_app
from app.model.trainer import MODEL_FILE, save_artifacts, train_model


@pytest.fixture(scope="session")
def trained():
    return train_model(rows=20000, iterations=300, seed=11)


@pytest.fixture(scope="session")
def model_dir(trained, tmp_path_factory):
    directory = tmp_path_factory.mktemp("model")
    save_artifacts(trained, directory)
    return directory


@pytest.fixture
def app(model_dir):
    return create_app({"TESTING": True, "MODEL_PATH": str(model_dir / MODEL_FILE)})


@pytest.fixture
def client(app):
    return app.test_client()


@pytest.fixture
def app_without_model(tmp_path):
    return create_app({"TESTING": True, "MODEL_PATH": str(tmp_path / "missing.cbm")})


@pytest.fixture
def client_without_model(app_without_model):
    return app_without_model.test_client()
