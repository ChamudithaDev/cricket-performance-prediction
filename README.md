# CricketAI – AI-Based Cricket Player Performance Prediction and Analytics System

CricketAI is a web-based cricket analytics platform that uses Artificial Intelligence (AI), Machine Learning (ML), and historical cricket statistics to predict individual player performance and provide meaningful performance insights.

The system is designed to support cricket players, coaches, selectors, and analysts by presenting player statistics, performance trends, player comparisons, match predictions, and data-driven insights through an interactive web interface.

---

## Project Overview

Traditional cricket analytics platforms mainly focus on match results, team statistics, and historical scorecards. CricketAI focuses on **individual player performance prediction and analysis**.

The system combines historical cricket data with available match information to generate player performance estimates and analytical insights.

### Main Objectives

- Predict individual cricket player performance.
- Analyse historical batting and bowling performance.
- Identify player strengths and weaknesses.
- Compare players under selected match conditions.
- Analyse toss decisions using historical match data.
- Present cricket statistics through interactive dashboards.
- Support data-driven cricket performance evaluation.

---

## System Architecture

The application follows a layered architecture that separates the frontend, main backend, and machine learning service.

```text
              USER
                |
                v
        REACT FRONTEND
                |
                v
       SPRING BOOT BACKEND
          /           \
         v             v
      MYSQL         FLASK ML API
                         |
                         v
                  TRAINED ML MODELS
                         |
                         v
                 PREDICTION RESULTS
```

### Architecture Components

| Component | Responsibility |
|---|---|
| React.js | User interface and interactive dashboards |
| Spring Boot | Main backend, REST APIs, authentication and business logic |
| MySQL | Structured application and cricket data storage |
| Flask | Exposes machine learning prediction endpoints |
| Python ML Pipeline | Data preprocessing, feature engineering, model training and evaluation |

**Important:** The React frontend communicates with Spring Boot. Spring Boot communicates with Flask when machine learning predictions are required.

---

## Technologies Used

| Technology | Purpose |
|---|---|
| React.js | Frontend development |
| Java | Backend programming |
| Spring Boot | REST API development and application services |
| Python | Machine learning and data processing |
| Flask | Machine learning API |
| MySQL | Relational database |
| Pandas | Data manipulation and preprocessing |
| NumPy | Numerical operations |
| Scikit-learn | Machine learning and evaluation |
| CatBoost | Gradient boosting classification and experimentation |
| Support Vector Regression (SVR) | Regression model |
| Chart.js | Interactive data visualisation |
| Axios | Frontend API communication |
| JWT | Authentication |
| Git and GitHub | Version control and source-code management |
| Postman | API testing |

---

## Main Features

### 1. User Authentication

- User registration and login.
- JWT-based authentication.
- Protected application routes.
- User session management and logout.

### 2. Player Search and Profiles

- Search for cricket players.
- View available player information.
- Access historical player statistics.

### 3. Player Performance Prediction

- Select a target player.
- Select the opposing team.
- Select the match format.
- Optionally specify the match venue.
- Generate estimated runs and a performance classification.

### 4. Player Analytics

- Analyse historical batting and bowling performance.
- Examine recent performance trends.
- Evaluate batting consistency and bowling effectiveness.
- Identify player strengths and weaknesses.

### 5. Player Comparison

- Compare two cricket players.
- Evaluate batting and bowling statistics.
- Examine differences in performance under selected conditions.

### 6. Toss Decision Advisor

- Analyse historical toss and match outcomes.
- Examine batting-first and chasing results.
- Provide a data-based toss decision recommendation for a selected venue and format.

### 7. Dashboard and Visualisation

- Centralised navigation.
- Player analytics overview.
- Prediction access.
- Interactive charts and statistical summaries.

---

## Machine Learning Approach

CricketAI uses two different machine learning tasks.

### Regression

Regression predicts a numerical value representing the player's estimated runs in the target match.

**Experimental model candidates:**
- Support Vector Regression (SVR)
- CatBoost Regressor
- Random Forest Regressor
- LightGBM Regressor

### Classification

Classification assigns a predicted performance category based on the target run total.

**Experimental model candidates:**
- CatBoost Classifier
- Random Forest Classifier
- LightGBM Classifier
- Multi-Layer Perceptron (MLP)

### Model Evaluation

The following evaluation metrics are used:

| Task | Evaluation Metrics |
|---|---|
| Regression | MAE, RMSE, R² Score |
| Classification | Accuracy, Precision, Recall, Macro-F1, Weighted-F1 |

### Experimental Results

The following are results from the Phase 2 experimental pipeline, evaluated on its repaired chronological dataset.

| Model | Metric | Test Result |
|---|---|---:|
| SVR Regression | MAE | 14.7976 |
| SVR Regression | RMSE | 25.2173 |
| SVR Regression | R² | 0.1666 |
| CatBoost Regression | MAE | 15.5345 |
| CatBoost Regression | RMSE | 23.4912 |
| CatBoost Regression | R² | 0.2768 |
| CatBoost Classification | Accuracy | 44.40% |
| CatBoost Classification | Macro-F1 | 0.4099 |

**Note:** These results belong to the experimental `model_v2` pipeline. They should not be interpreted as the performance of the currently deployed production models unless the experimental models have subsequently been promoted and verified.

---

## Dataset and Data Sources

The project uses cricket datasets and external cricket data services for historical and player-related information.

### Cricsheet

Cricsheet provides structured ball-by-ball cricket match data.

Project applications include:

- Historical match analysis.
- Player batting and bowling statistics.
- Player-bowler matchup analysis.
- Batting and bowling phase analysis.
- Toss decision analysis.

Website: https://cricsheet.org/

### External Cricket Data API

The application also integrates an external cricket data API to obtain available player and cricket information.

The API is used to complement the historical dataset with available current information.

---

## Experimental Machine Learning Features

The Phase 2 experimental pipeline uses 11 pre-match features:

| Feature | Description |
|---|---|
| `avg_runs_last_5` | Average runs in the previous five matches |
| `avg_sr_last_5` | Average strike rate in the previous five matches |
| `avg_wickets_last_5` | Average wickets in the previous five matches |
| `avg_economy_last_5` | Average bowling economy in the previous five matches |
| `career_matches_played` | Number of previous matches played |
| `career_avg_runs` | Historical average runs |
| `venue_avg_runs` | Smoothed historical average at the venue |
| `opponent_avg_runs` | Smoothed historical average against the opponent |
| `match_type_odi` | ODI format indicator |
| `match_type_t20i` | T20I format indicator |
| `match_type_test` | Test format indicator |

Historical features are calculated using information available before the target match. The experimental pipeline uses chronological data splitting to reduce data leakage.

---

## Machine Learning Experimental Pipeline

The experimental model development process follows these stages:

```text
Historical Cricket Dataset
          |
          v
Data Cleaning and Aggregation
          |
          v
Feature Engineering
          |
          v
Chronological Data Splitting
          |
          v
Model Training and Validation
          |
          v
Model Selection
          |
          v
Final Holdout Evaluation
          |
          v
Experimental Model Artifacts
```

The Phase 2 experiment uses three chronological datasets:

- Training: 5,526 records
- Validation: 1,286 records
- Final testing: 1,966 records

The final test set is kept separate from model selection.

---

## API Structure

### Spring Boot Authentication Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/auth/register` | Register a user |
| POST | `/api/auth/login` | Authenticate a user |

### Flask Machine Learning Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/ml/health` | Check ML service availability |
| POST | `/api/ml/predict` | Generate a player performance prediction |
| GET | `/api/ml/players` | Retrieve available player options |
| GET | `/api/ml/venues` | Retrieve available venue options |
| GET | `/api/ml/model-info` | Retrieve model information |

The frontend should access application functionality through Spring Boot rather than directly calling Flask.

---

## Project Structure

The project is organised into three main application components.

```text
CricketAI/
│
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── context/
│   │   ├── pages/
│   │   ├── routes/
│   │   ├── services/
│   │   └── styles/
│   └── package.json
│
├── backend/
│   ├── src/
│   │   └── main/
│   └── pom.xml
│
├── ml-service/
│   ├── api/
│   ├── models/
│   ├── data/
│   ├── experiments/
│   │   └── model_v2/
│   └── requirements.txt
│
├── .gitignore
└── README.md
```

*The structure above is a simplified overview. Actual folders and filenames may differ in the repository.*

---

## Installation and Setup

### Prerequisites

Install the following software:

- Java 21
- Maven
- Node.js and npm
- Python 3.11+
- MySQL 8
- Git

### 1. Clone the Repository

```bash
git clone https://github.com/ChamudithaDev/cricket-performance-prediction.git
```

```bash
cd cricket-performance-prediction
```

### 2. Configure the Database

1. Install and start MySQL.
2. Create the database using the database name configured in the backend.
3. Configure database connection properties in the backend's local environment configuration.
4. Ensure database credentials are not committed to GitHub.

### 3. Run the Spring Boot Backend

Navigate to the backend directory:

```bash
cd backend
```

Run the application using Maven:

```bash
mvn spring-boot:run
```

### 4. Run the Flask ML Service

Open another terminal and navigate to the ML service directory:

```bash
cd ml-service
```

Create a virtual environment:

```bash
python -m venv venv
```

Activate it on Windows:

```bash
venv\Scripts\activate
```

Install dependencies:

```bash
pip install -r requirements.txt
```

Run the Flask application using the project's configured entry point.

### 5. Run the React Frontend

Open another terminal:

```bash
cd frontend
```

Install dependencies:

```bash
npm install
```

Configure the frontend API base URL using the project's environment configuration.

Start the development server:

```bash
npm run dev
```

Open the local URL displayed in the terminal.

---

## Testing

The project uses different testing approaches for its application components.

- Backend tests using the Spring Boot testing framework.
- REST API testing using Postman.
- Frontend production build verification.
- Machine learning pipeline tests using Python `unittest`.
- Model evaluation using independent chronological test data.

Run the experimental pipeline tests from the project root using the configured Python environment:

```bash
python -m unittest ml-service/experiments/model_v2/tests/test_pipeline_v2.py
```

---

## Current Limitations

- Cricket performance is inherently uncertain, and predictions are estimates rather than guaranteed outcomes.
- Historical dataset coverage may not represent every match or player career.
- Test cricket has different scoring characteristics from limited-overs formats.
- Some classification categories are more difficult to predict than others.
- Experimental models are maintained separately from production models until they are independently validated and approved for deployment.
- The availability and freshness of external cricket data depend on the API provider.

---

## Future Enhancements

Potential future improvements include:

- Format-specific machine learning models.
- Improved player-role and playing-condition features where reliable data is available.
- Enhanced prediction explanations using explainable AI techniques.
- Additional player performance visualisations.
- Expanded live match data integration.
- Further model evaluation and optimisation.
- More comprehensive user and analytics features.

---

## Author and Project Information

**Project:** CricketAI – AI-Based Cricket Player Performance Prediction and Analytics System

**Academic Level:** Final-Year Undergraduate Software Engineering Project

**GitHub:** https://github.com/ChamudithaDev

---

## Disclaimer

CricketAI is an academic software engineering and machine learning project. Predictions and analytical outputs are generated from available historical and contextual data and should be treated as estimates, not guarantees of future player performance.
