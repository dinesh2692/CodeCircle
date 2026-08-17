package com.codecircle.project;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="projects") public class Project { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(nullable=false) public String owner; @Column(nullable=false,length=120) public String name; @Column(nullable=false,columnDefinition="text") public String code="public class Main { public static void main(String[] args) { } }"; public Instant updatedAt=Instant.now(); }
