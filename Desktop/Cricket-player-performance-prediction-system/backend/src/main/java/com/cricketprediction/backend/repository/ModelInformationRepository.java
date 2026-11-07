package com.cricketprediction.backend.repository;

import com.cricketprediction.backend.model.ModelInformation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelInformationRepository extends JpaRepository<ModelInformation, Long> {

    /** All metric rows for a given model name and type. */
    List<ModelInformation> findByModelNameAndModelType(String modelName, String modelType);

    /** All metric rows for a given task type (e.g. "regression"). */
    List<ModelInformation> findByModelType(String modelType);
}
