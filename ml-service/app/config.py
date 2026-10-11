import os


class Config:
    SERVICE_NAME = os.getenv("ML_SERVICE_NAME", "no-show-ml")
    SERVICE_VERSION = os.getenv("ML_SERVICE_VERSION", "0.1.0")
