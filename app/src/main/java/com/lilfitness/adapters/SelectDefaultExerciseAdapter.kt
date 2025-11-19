package com.lilfitness.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import com.lilfitness.models.ExerciseLibraryItem

class SelectDefaultExerciseAdapter(
    context: Context,
    private val exercises: List<ExerciseLibraryItem>,
    private val selectedExercises: MutableSet<ExerciseLibraryItem>
) : ArrayAdapter<ExerciseLibraryItem>(context, 0, exercises) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_multiple_choice, parent, false)
        val checkedTextView = view.findViewById<CheckedTextView>(android.R.id.text1)
        val exercise = exercises[position]

        checkedTextView.text = exercise.name
        checkedTextView.isChecked = selectedExercises.contains(exercise)

        return view
    }
}