package com.codecircle.auth;
import jakarta.persistence.*;
@Entity @Table(name="users")
public class AppUser { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(unique=true,nullable=false,length=64) public String username; @Column(nullable=false) public String passwordHash; public AppUser(){} public AppUser(String u,String p){username=u;passwordHash=p;} }
