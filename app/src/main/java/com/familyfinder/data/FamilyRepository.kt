package com.familyfinder.data

import kotlinx.coroutines.flow.Flow

class FamilyRepository(private val dao: FamilyDao) {
    val allMembers: Flow<List<FamilyMember>> = dao.getAllMembers()

    suspend fun insert(member: FamilyMember) = dao.insert(member)
    suspend fun update(member: FamilyMember) = dao.update(member)
    suspend fun delete(member: FamilyMember) = dao.delete(member)
    suspend fun getCount() = dao.getCount()
}
