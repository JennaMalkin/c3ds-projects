extends Control

func _ready():
	for v in range(255):
		var chem = preload("chemical.tscn").instance()
		chem.chemical_id = v + 1
		$tc/All/gc.add_child(chem)

func _process(_delta):
	if ChemicalTracker.last_request != null:
		$gv/CPXErrorBox.update_from(ChemicalTracker.last_request)
