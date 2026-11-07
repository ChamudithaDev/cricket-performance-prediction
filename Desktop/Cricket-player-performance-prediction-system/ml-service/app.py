from flask import Flask
from flask_cors import CORS
from api.predict_routes import predict_bp
from api.metadata_routes import metadata_bp
from api.report_routes import report_bp


def create_app():
    app = Flask(__name__)
    CORS(app)
    app.register_blueprint(predict_bp, url_prefix="/api/ml")
    app.register_blueprint(metadata_bp, url_prefix="/api/ml")
    app.register_blueprint(report_bp, url_prefix="/api/ml/reports")
    return app


if __name__ == "__main__":
    app = create_app()
    app.run(host="0.0.0.0", port=5000, debug=True)