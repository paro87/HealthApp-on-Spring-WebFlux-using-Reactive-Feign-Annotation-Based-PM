package com.paro.hospitalservice.service;

import com.paro.hospitalservice.client.DepartmentClient;
import com.paro.hospitalservice.client.PatientClient;
import com.paro.hospitalservice.model.Department;
import com.paro.hospitalservice.model.Hospital;
import com.paro.hospitalservice.model.Patient;
import com.paro.hospitalservice.repository.HospitalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.hystrix.HystrixCommands;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;


@Service
public class HospitalService {
    private static final Logger LOGGER= LoggerFactory.getLogger(HospitalService.class);

    private final HospitalRepository hospitalRepository;
    private final DepartmentClient departmentClient;
    private final PatientClient patientClient;
    @Autowired
    public HospitalService (HospitalRepository hospitalRepository, DepartmentClient departmentClient, PatientClient patientClient){
        this.hospitalRepository=hospitalRepository;
        this.departmentClient=departmentClient;
        this.patientClient=patientClient;
    }

    public Flux<Hospital> getAll(){
        LOGGER.info("Hospitals found");
        return hospitalRepository.findAll();
    }

    public Mono<Hospital> getByHospitalId(Long hospitalId){
        Mono<Hospital> hospitalFound=hospitalRepository.findByHospitalId(hospitalId);
        LOGGER.info("Hospital found with id={}", hospitalId);
        return hospitalFound;
    }

    public Mono<Hospital> add(Mono<Hospital> hospital){
        Mono<Hospital> hospitalSaved=hospitalRepository.saveAll(hospital).next();
        return hospitalSaved;
    }

    public Mono<Hospital> put(Long hospitalId, Mono<Hospital> hospitalToPut) {
        return hospitalRepository.findByHospitalId(hospitalId)
                .flatMap(hospital ->hospitalRepository.delete(hospital))
                .then(hospitalRepository.saveAll(hospitalToPut).next());

        // Possible 2nd solution
        /*
        Mono<Hospital> hospitalFound=hospitalRepository.findByHospitalId(hospitalId);
        Mono<Hospital> hospitalPut=hospitalFound.map(hospitalFromRepo -> {
            hospitalToPut.map(hospitalBeingPut -> {
                if (hospitalBeingPut.getName()!=null) {
                    hospitalFromRepo.setName(hospitalBeingPut.getName());
                }
                if (hospitalBeingPut.getAddress()!=null) {
                    hospitalFromRepo.setAddress(hospitalBeingPut.getAddress());
                }
                if (hospitalBeingPut.getDepartmentId()!=null) {
                    hospitalFromRepo.setDepartmentId(hospitalBeingPut.getDepartmentId());
                }
                if (hospitalBeingPut.getPatientList()!=null) {
                    hospitalFromRepo.setPatientList(hospitalBeingPut.getPatientList());
                }
                return hospitalFromRepo;
            }).subscribe();
            return hospitalFromRepo;
        });
        return hospitalRepository.saveAll(hospitalPut).next();*/
    }

    //1-Patch: Hospital object
    /*
    public Mono<Hospital> patch(Long hospitalId, Hospital hospitalToPatch) {
        Mono<Hospital> hospitalFound=hospitalRepository.findByHospitalId(hospitalId);
        Mono<Hospital> hospitalPatched=hospitalFound.map(hospital -> {
            if (hospitalToPatch.getHospitalId()!=null) {
                hospital.setHospitalId(hospitalToPatch.getHospitalId());
            }
            if (hospitalToPatch.getName()!=null) {
                hospital.setName(hospitalToPatch.getName());
            }
            if (hospitalToPatch.getAddress()!=null) {
                hospital.setAddress(hospitalToPatch.getAddress());
            }
            if (hospitalToPatch.getDepartmentId()!=null) {
                hospital.setDepartmentId(hospitalToPatch.getDepartmentId());
            }
            if (hospitalToPatch.getPatientList()!=null) {
                hospital.setPatientList(hospitalToPatch.getPatientList());
            }
            return hospital;
        });
        return hospitalRepository.saveAll(hospitalPatched).next();
    }
    */


    //2-Patch: Mono<Hospital> object
    public Mono<Hospital> patch(Long hospitalId, Mono<Hospital> hospitalToPatch) {
        Mono<Hospital> hospitalFound=hospitalRepository.findByHospitalId(hospitalId);
        Mono<Hospital> hospitalPatched=hospitalFound.map(hospitalFromRepo -> {

            hospitalToPatch.map(hospitalBeingPatched -> {

                if (hospitalBeingPatched.getHospitalId()!=null) {
                    hospitalFromRepo.setHospitalId(hospitalBeingPatched.getHospitalId());
                }
                if (hospitalBeingPatched.getName()!=null) {
                    hospitalFromRepo.setName(hospitalBeingPatched.getName());
                }
                if (hospitalBeingPatched.getAddress()!=null) {
                    hospitalFromRepo.setAddress(hospitalBeingPatched.getAddress());
                }
                if (hospitalBeingPatched.getDepartmentList()!=null) {
                    hospitalFromRepo.setDepartmentList(hospitalBeingPatched.getDepartmentList());
                }
                if (hospitalBeingPatched.getPatientList()!=null) {
                    hospitalFromRepo.setPatientList(hospitalBeingPatched.getPatientList());
                }
                return hospitalFromRepo;
            }).subscribe();
            return hospitalFromRepo;
        });
        return hospitalRepository.saveAll(hospitalPatched).next();
    }

    public void deleteById(Long hospitalId) {
        Mono<Hospital> hospitalToBeDeleted=hospitalRepository.findByHospitalId(hospitalId);
        hospitalToBeDeleted.flatMap(hospital ->hospitalRepository.delete(hospital)).subscribe();
    }


    public Mono<Hospital> getHospitalWithDepartments(Long hospitalId){
        Mono<Hospital> hospitalList=hospitalRepository.findByHospitalId(hospitalId);
        LOGGER.info("Departments found with hospital id={}", hospitalId);
        return hospitalList.flatMap(hospital ->{
            Flux<Department> departmentFlux = departmentClient.findByHospital(hospital.getHospitalId());
            return departmentFlux.collectList()
                    .map(list->{
                        hospital.setDepartmentList(list);
                        return hospital;
                    });
        });


    }


    public Mono<Hospital> getHospitalWithDepartmentsAndPatients(Long hospitalId){
        Mono<Hospital> hospitalList=hospitalRepository.findByHospitalId(hospitalId);
        return hospitalList.flatMap(hospital ->{
            Flux<Department> departmentFlux = departmentClient.findByHospitalWithPatients(hospital.getHospitalId());
            return departmentFlux.collectList()
                    .map(list->{
                        hospital.setDepartmentList(list);
                        return hospital;
                    });
        });
    }


    public Mono<Hospital> getHospitalWithPatients(Long hospitalId){
        Mono<Hospital> hospitalList=hospitalRepository.findByHospitalId(hospitalId);
        return hospitalList.flatMap(hospital ->{
            Flux<Patient> patientFlux = patientClient.findByHospital(hospital.getHospitalId());
            return patientFlux.collectList()
                    .map(list->{
                        hospital.setPatientList(list);
                        return hospital;
                    });
        });

    }
}
